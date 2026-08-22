package com.solace.world;

import com.mojang.logging.LogUtils;
import com.solace.access.BookChoiceLevelsAccess;
import com.solace.access.EnchantedShelfSlotsAccess;
import com.solace.config.EnchantingReworkConfig;
import com.solace.mixin.AbstractContainerMenuAccessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import net.backupcup.mcde.util.Choice;
import net.backupcup.mcde.util.SlotPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class EnchantingReworkSupport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ROLL_BENCH = id("mcde", "roll_bench");
    private static final ResourceLocation ATONING_TABLE = id("caverns_and_chasms", "atoning_table");
    private static final String BOOK_LAYOUT_TAG = "SolaceBookshelfEnchanting";
    private static final String BOOK_LAYOUT_FINGERPRINT_KEY = "Fingerprint";
    private static final String BOOK_LAYOUT_CHOICES_KEY = "Sources";
    private static final String BOOK_SOURCE_ENCHANTMENT_KEY = "Enchantment";
    private static final String BOOK_SOURCE_LEVEL_KEY = "Level";
    private static final String BOOK_SOURCE_POSITION_KEY = "ShelfPosition";
    private static final String BOOK_SOURCE_SLOT_KEY = "ShelfSlot";
    private static final String BOOK_LAYOUT_VERSION_KEY = "Version";
    private static final int BOOK_LAYOUT_VERSION = 1;

    private EnchantingReworkSupport() {
    }

    public static MenuProvider enchantingTableProvider(Level level, BlockPos pos) {
        return menuProvider("net.backupcup.mcde.screen.handler.RollBenchScreenHandler", "block.minecraft.enchanting_table", level, pos);
    }

    public static MenuProvider rollBenchProvider(Level level, BlockPos pos) {
        return menuProvider("net.backupcup.mcde.screen.handler.RollBenchScreenHandler", "block.caverns_and_chasms.atoning_table", level, pos);
    }

    public static Optional<ContainerLevelAccess> context(Object handler) {
        return field(handler, "context", ContainerLevelAccess.class);
    }

    public static Optional<Container> inventory(Object handler) {
        return field(handler, "inventory", Container.class);
    }

    public static void installSlotNormalizer(AbstractContainerMenu menu, ContainerLevelAccess context, Player player) {
        if (!isEnchantingTable(context)) {
            return;
        }

        menu.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack stack) {
                Optional<Container> inventory = inventory(handler);
                if (slotId == 0 && inventory.isPresent()) {
                    ItemStack actualStack = inventory.get().getItem(0);
                    boolean changed = normalizeBookshelfStack(actualStack, context, false);
                    if (changed) {
                        inventory.get().setChanged();
                        scheduleHandlerSync(handler, context, player);
                    }
                }
            }

            @Override
            public void dataChanged(AbstractContainerMenu handler, int property, int value) {
            }
        });
    }

    public static void synchronizeHandlerInventory(Object handler, Player player) {
        inventory(handler).ifPresent(Container::setChanged);
        if (handler instanceof AbstractContainerMenu menu) {
            menu.broadcastFullState();
        }
        refreshBookshelfLocks(handler, player);
    }

    private static void scheduleHandlerSync(AbstractContainerMenu handler, ContainerLevelAccess context, Player player) {
        context.evaluate((level, pos) -> {
            if (!level.isClientSide() && level.getServer() != null) {
                level.getServer().execute(() -> {
                    handler.broadcastFullState();
                    refreshBookshelfLocks(handler, player);
                });
            }
            return true;
        }, false);
    }

    private static void refreshBookshelfLocks(Object handler, Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        inventory(handler).ifPresent(inventory -> {
            try {
                Optional<?> slots = mcdeSlots(inventory.getItem(0));
                if (slots.isEmpty()) {
                    return;
                }
                Method sendLockedSlots = handler.getClass().getDeclaredMethod(
                        "sendLockedSlots", slots.get().getClass(), Player.class);
                sendLockedSlots.setAccessible(true);
                sendLockedSlots.invoke(handler, slots.get(), player);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.debug("[Solace] Could not refresh bookshelf slot locks", e);
            }
        });
    }

    public static boolean stillValidAtEnchantingTable(Object handler, Player player) {
        Optional<ContainerLevelAccess> context = context(handler);
        return context.isPresent()
                && AbstractContainerMenuAccessor.invokeStillValid(
                        context.get(), player, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE);
    }

    public static boolean stillValidAtAtoningTable(Object handler, Player player) {
        Optional<ContainerLevelAccess> context = context(handler);
        return context.isPresent()
                && (stillValidAt(context.get(), player, ATONING_TABLE)
                || stillValidAt(context.get(), player, ROLL_BENCH));
    }

    public static boolean stillValidAtEnchantingOrAtoningTable(Object handler, Player player) {
        return stillValidAtEnchantingTable(handler, player) || stillValidAtAtoningTable(handler, player);
    }

    public static boolean isEnchantingTable(ContainerLevelAccess context) {
        return context.evaluate((level, pos) -> level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE), false);
    }

    public static boolean isBookshelfHandler(Object handler) {
        Optional<Container> inventory = inventory(handler);
        return inventory.isPresent() && hasBookLayout(inventory.get().getItem(0));
    }

    private static boolean normalizeBookshelfStack(ItemStack stack, ContainerLevelAccess context, boolean reroll) {
        if (stack.isEmpty()) {
            return false;
        }
        return context.evaluate((level, pos) -> rebuildBookshelfLayout(level, pos, stack, reroll), false);
    }

    public static boolean rerollBookshelfChoices(Object handler) {
        Optional<ContainerLevelAccess> context = context(handler);
        Optional<Container> inventory = inventory(handler);
        if (context.isEmpty() || inventory.isEmpty()) {
            return false;
        }
        return normalizeBookshelfStack(inventory.get().getItem(0), context.get(), true);
    }

    public static boolean handleBookshelfClick(Object handler, Player player, int buttonId) {
        Optional<ContainerLevelAccess> context = context(handler);
        Optional<Container> inventory = inventory(handler);
        if (context.isEmpty() || inventory.isEmpty() || player.level().isClientSide()) {
            return false;
        }

        return context.get().evaluate((level, tablePos) -> {
            ItemStack item = inventory.get().getItem(0);
            ItemStack lapis = inventory.get().getItem(1);
            if (item.isEmpty() || !hasBookLayout(item)) {
                return false;
            }

            if (buttonId == -1) {
                int openCapacity = openChoiceCapacity(item);
                int candidateCount = applicableBooks(level, tablePos, item).size();
                if (candidateCount <= openCapacity || !canPay(player, lapis,
                        EnchantingReworkConfig.rerollLapisCost(), EnchantingReworkConfig.rerollXpCost())) {
                    return false;
                }
                if (!rerollBookshelfChoices(handler)) {
                    return false;
                }
                pay(player, lapis, EnchantingReworkConfig.rerollLapisCost(), EnchantingReworkConfig.rerollXpCost());
                inventory.get().setChanged();
                synchronizeHandlerInventory(handler, player);
                return true;
            }

            SlotPosition[] positions = SlotPosition.values();
            if (buttonId < 0 || buttonId >= positions.length * positions.length) {
                return false;
            }
            int mainIndex = buttonId / positions.length;
            int choiceIndex = buttonId % positions.length;
            SlotPosition mainPosition = positions[mainIndex];
            SlotPosition choicePosition = positions[choiceIndex];

            Object slots;
            Object slot;
            Optional<?> chosen;
            ResourceLocation enchantmentId;
            int targetLevel;
            BookSource source;
            SlotPosition selectedPosition;
            try {
                Optional<?> slotsOptional = mcdeSlots(item);
                if (slotsOptional.isEmpty()) {
                    return false;
                }
                slots = slotsOptional.get();
                Optional<?> slotOptional = optionalResult(slots, "getEnchantmentSlot", slotPositionClass(), mainPosition);
                if (slotOptional.isEmpty()) {
                    return false;
                }
                slot = slotOptional.get();
                chosen = optionalResult(slot, "getChosen");

                if (chosen.isPresent()) {
                    enchantmentId = toResourceLocation(method(chosen.get(), "getEnchantmentId").invoke(chosen.get()));
                    targetLevel = intResult(slot, "getLevel") + 1;
                    source = findUpgradeBook(level, tablePos, enchantmentId).orElse(null);
                    selectedPosition = (SlotPosition) method(chosen.get(), "getChoicePosition").invoke(chosen.get());
                } else {
                    Optional<?> clickedEnchantment = optionalResult(slot, "getChoice", slotPositionClass(), choicePosition);
                    source = readBookSource(item, mainPosition, choicePosition).orElse(null);
                    if (clickedEnchantment.isEmpty() || source == null
                            || !toResourceLocation(clickedEnchantment.get()).equals(source.enchantmentId())) {
                        rebuildBookshelfLayout(level, tablePos, item, false);
                        inventory.get().setChanged();
                        synchronizeHandlerInventory(handler, player);
                        return false;
                    }
                    enchantmentId = source.enchantmentId();
                    targetLevel = source.level();
                    selectedPosition = choicePosition;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.debug("[Solace] Could not inspect bookshelf enchanting choice", e);
                return false;
            }

            if (source == null || !nearbyEnchantedBooks(level, tablePos).contains(source)) {
                rebuildBookshelfLayout(level, tablePos, item, false);
                inventory.get().setChanged();
                synchronizeHandlerInventory(handler, player);
                return false;
            }

            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(enchantmentId);
            if (enchantment == null || targetLevel < enchantment.getMinLevel() || targetLevel > enchantment.getMaxLevel()) {
                return false;
            }

            int baseXpCost;
            try {
                baseXpCost = enchantCost(enchantmentId, targetLevel);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.debug("[Solace] Could not calculate bookshelf enchanting cost", e);
                return false;
            }
            int xpCost = Math.max(1, baseXpCost);
            int lapisCost = EnchantingReworkConfig.enchantingLapisCost();
            if (!canPay(player, lapis, lapisCost, xpCost)) {
                return false;
            }

            try {
                if (chosen.isPresent()) {
                    method(slot, "setLevel", int.class).invoke(slot, targetLevel);
                } else if (!(Boolean) method(slot, "setChosen", slotPositionClass(), int.class)
                        .invoke(slot, selectedPosition, targetLevel)) {
                    return false;
                }
                ((BookChoiceLevelsAccess) slot).solace$setChoiceLevel(selectedPosition, targetLevel);
                method(slots, "updateItemStack", ItemStack.class).invoke(slots, item);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.debug("[Solace] Could not apply bookshelf enchantment", e);
                return false;
            }

            if (!player.isCreative()) {
                if (!consumeBook(level, source)) {
                    rebuildBookshelfLayout(level, tablePos, item, false);
                    inventory.get().setChanged();
                    synchronizeHandlerInventory(handler, player);
                    return false;
                }
                pay(player, lapis, lapisCost, xpCost);
            }

            rebuildBookshelfLayout(level, tablePos, item, false);
            inventory.get().setChanged();
            synchronizeHandlerInventory(handler, player);
            level.playSound(null, tablePos, net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
            return true;
        }, false);
    }

    public static boolean canPayForBookshelfAction(Object handler, Player player) {
        Optional<Container> inventory = inventory(handler);
        if (inventory.isEmpty()) {
            return false;
        }
        return canPay(player, inventory.get().getItem(1), EnchantingReworkConfig.enchantingLapisCost(), 0);
    }

    public static int bookshelfChoiceXpCost(Choice choice) {
        int sourceLevel = ((BookChoiceLevelsAccess) choice.getEnchantmentSlot())
                .solace$getChoiceLevel(choice.getChoicePosition());
        if (sourceLevel <= 0) {
            return -1;
        }

        try {
            int targetLevel = choice.isChosen() ? choice.getLevel() + 1 : sourceLevel;
            ResourceLocation enchantmentId = toResourceLocation(method(choice, "getEnchantmentId").invoke(choice));
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(enchantmentId);
            if (enchantment == null || targetLevel < enchantment.getMinLevel()
                    || targetLevel > enchantment.getMaxLevel()) {
                return -1;
            }
            return Math.max(1, enchantCost(enchantmentId, targetLevel));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not calculate bookshelf tooltip XP cost", e);
            return -1;
        }
    }

    public static Optional<Component> bookshelfUpgradeLabel(Choice choice) {
        int sourceLevel = ((BookChoiceLevelsAccess) choice.getEnchantmentSlot())
                .solace$getChoiceLevel(choice.getChoicePosition());
        if (sourceLevel <= 0 || !choice.isChosen()) {
            return Optional.empty();
        }

        try {
            ResourceLocation enchantmentId = toResourceLocation(method(choice, "getEnchantmentId").invoke(choice));
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(enchantmentId);
            int currentLevel = choice.getEnchantmentSlot().getLevel();
            int nextLevel = currentLevel + 1;
            if (enchantment == null || nextLevel > enchantment.getMaxLevel()) {
                return Optional.empty();
            }
            Component currentName = enchantment.getFullname(currentLevel);
            if (currentLevel == 1 && enchantment.getMaxLevel() > 1) {
                currentName = currentName.copy()
                        .append(" ")
                        .append(Component.translatable("enchantment.level.1"));
            }
            return Optional.of(currentName.copy()
                    .append(" -> ")
                    .append(Component.translatable("enchantment.level." + nextLevel)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not build bookshelf upgrade tooltip", e);
            return Optional.empty();
        }
    }

    public static boolean isBookshelfChoiceSlotLocked(Object handler, Choice choice) {
        if (!isBookshelfHandler(handler)) {
            return true;
        }
        try {
            Optional<?> locked = (Optional<?>) method(handler, "isSlotLocked", slotPositionClass())
                    .invoke(handler, choice.getEnchantmentSlot().getSlotPosition());
            return locked.isEmpty() || Boolean.TRUE.equals(locked.get());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not inspect bookshelf tooltip slot lock", e);
            return true;
        }
    }

    public static Optional<ResourceLocation> bookshelfSlotAction(Object handler, SlotPosition mainPosition) {
        Optional<ContainerLevelAccess> context = context(handler);
        Optional<Container> inventory = inventory(handler);
        if (context.isEmpty() || inventory.isEmpty()) {
            return Optional.empty();
        }

        ItemStack item = inventory.get().getItem(0);
        try {
            Optional<?> slots = mcdeSlots(item);
            if (slots.isEmpty()) {
                return Optional.empty();
            }
            Optional<?> slot = optionalResult(slots.get(), "getEnchantmentSlot", slotPositionClass(), mainPosition);
            if (slot.isEmpty()) {
                return Optional.empty();
            }
            Optional<?> chosen = optionalResult(slot.get(), "getChosen");
            if (chosen.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Object> choices = (List<Object>) method(slot.get(), "choices").invoke(slot.get());
                if (choices.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(toResourceLocation(method(choices.get(0), "getEnchantmentId").invoke(choices.get(0))));
            }

            int nextLevel = intResult(slot.get(), "getLevel") + 1;
            ResourceLocation enchantmentId = toResourceLocation(method(chosen.get(), "getEnchantmentId").invoke(chosen.get()));
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(enchantmentId);
            if (enchantment == null || nextLevel > enchantment.getMaxLevel()) {
                return Optional.empty();
            }
            return context.get().evaluate((level, tablePos) ->
                    findUpgradeBook(level, tablePos, enchantmentId).map(BookSource::enchantmentId), Optional.empty());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not inspect bookshelf slot availability", e);
            return Optional.empty();
        }
    }

    private static boolean rebuildBookshelfLayout(Level level, BlockPos tablePos, ItemStack item, boolean reroll) {
        if (level.isClientSide() || item.isEmpty()) {
            return false;
        }

        List<BookSource> nearbyBooks = nearbyEnchantedBooks(level, tablePos);
        int fingerprint = bookshelfFingerprint(item, nearbyBooks);
        try {
            Optional<?> current = mcdeSlots(item);
            if (!reroll && current.isPresent() && bookLayoutMatches(item, fingerprint)) {
                return false;
            }

            EnumMap<SlotPosition, ChosenState> chosen = chosenStates(current);
            Set<ResourceLocation> chosenIds = chosen.values().stream()
                    .map(ChosenState::enchantmentId)
                    .collect(Collectors.toSet());
            List<BookSource> candidates = applicableBooks(item, nearbyBooks, chosenIds);
            long seed = reroll ? level.random.nextLong() : (0x9E3779B97F4A7C15L ^ tablePos.asLong() ^ fingerprint);
            Collections.shuffle(candidates, new Random(seed));

            Object builder = Class.forName("net.backupcup.mcde.util.EnchantmentSlots").getMethod("builder").invoke(null);
            EnumMap<SlotPosition, List<BookSource>> assigned = new EnumMap<>(SlotPosition.class);
            int candidateIndex = 0;
            for (SlotPosition mainPosition : SlotPosition.values()) {
                ChosenState chosenState = chosen.get(mainPosition);
                if (chosenState != null) {
                    invokeWithSlot(builder, mainPosition, List.of(chosenState.enchantmentId()));
                    continue;
                }

                List<BookSource> choices = new ArrayList<>(3);
                while (choices.size() < 3 && candidateIndex < candidates.size()) {
                    choices.add(candidates.get(candidateIndex++));
                }
                if (!choices.isEmpty()) {
                    addBookSlot(builder, mainPosition, choices);
                    assigned.put(mainPosition, choices);
                }
            }

            Object built = method(builder, "build").invoke(builder);
            for (Map.Entry<SlotPosition, ChosenState> entry : chosen.entrySet()) {
                Optional<?> slot = optionalResult(built, "getEnchantmentSlot", slotPositionClass(), entry.getKey());
                if (slot.isPresent()) {
                    method(slot.get(), "setChosen", slotPositionClass(), int.class)
                            .invoke(slot.get(), SlotPosition.FIRST, entry.getValue().level());
                    ((BookChoiceLevelsAccess) slot.get()).solace$setChoiceLevel(SlotPosition.FIRST, entry.getValue().level());
                }
            }

            CompoundTag sourceTags = new CompoundTag();
            for (Map.Entry<SlotPosition, List<BookSource>> entry : assigned.entrySet()) {
                Optional<?> slot = optionalResult(built, "getEnchantmentSlot", slotPositionClass(), entry.getKey());
                if (slot.isEmpty()) {
                    continue;
                }
                for (int choiceIndex = 0; choiceIndex < entry.getValue().size(); choiceIndex++) {
                    SlotPosition choicePosition = SlotPosition.values()[choiceIndex];
                    BookSource source = entry.getValue().get(choiceIndex);
                    ((BookChoiceLevelsAccess) slot.get()).solace$setChoiceLevel(choicePosition, source.level());
                    sourceTags.put(sourceKey(entry.getKey(), choicePosition), source.toTag());
                }
            }

            int lapisCost = Math.max(0, EnchantingReworkConfig.enchantingLapisCost());
            method(built, "setNextRerollCost", int.class).invoke(built, lapisCost);
            method(built, "setNextRerollCostPowerful", int.class).invoke(built, lapisCost);
            method(built, "updateItemStack", ItemStack.class).invoke(built, item);
            writeBookLayout(item, fingerprint, sourceTags);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not rebuild bookshelf enchantments", e);
            return false;
        }
    }

    private static EnumMap<SlotPosition, ChosenState> chosenStates(Optional<?> slots) throws ReflectiveOperationException {
        EnumMap<SlotPosition, ChosenState> chosen = new EnumMap<>(SlotPosition.class);
        if (slots.isEmpty()) {
            return chosen;
        }
        for (SlotPosition position : SlotPosition.values()) {
            Optional<?> slot = optionalResult(slots.get(), "getEnchantmentSlot", slotPositionClass(), position);
            if (slot.isEmpty()) {
                continue;
            }
            Optional<?> selected = optionalResult(slot.get(), "getChosen");
            if (selected.isPresent()) {
                ResourceLocation enchantmentId = toResourceLocation(method(selected.get(), "getEnchantmentId").invoke(selected.get()));
                chosen.put(position, new ChosenState(enchantmentId, intResult(slot.get(), "getLevel")));
            }
        }
        return chosen;
    }

    private static void addBookSlot(Object builder, SlotPosition mainPosition, List<BookSource> choices)
            throws ReflectiveOperationException {
        invokeWithSlot(builder, mainPosition, choices.stream().map(BookSource::enchantmentId).toList());
    }

    private static List<BookSource> applicableBooks(Level level, BlockPos tablePos, ItemStack item) {
        try {
            Optional<?> slots = mcdeSlots(item);
            Set<ResourceLocation> chosenIds = chosenStates(slots).values().stream()
                    .map(ChosenState::enchantmentId)
                    .collect(Collectors.toSet());
            return applicableBooks(item, nearbyEnchantedBooks(level, tablePos), chosenIds);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not inspect locked bookshelf enchantments", e);
            return List.of();
        }
    }

    private static List<BookSource> applicableBooks(ItemStack item, List<BookSource> nearbyBooks,
                                                     Set<ResourceLocation> chosenIds) {
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(item);
        Set<ResourceLocation> existingIds = existing.keySet().stream()
                .map(BuiltInRegistries.ENCHANTMENT::getKey)
                .collect(Collectors.toSet());
        Set<ResourceLocation> offeredIds = new HashSet<>();
        List<BookSource> applicable = new ArrayList<>();
        for (BookSource source : nearbyBooks) {
            if (chosenIds.contains(source.enchantmentId()) || existingIds.contains(source.enchantmentId())) {
                continue;
            }
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(source.enchantmentId());
            if (enchantment == null || source.level() != Math.max(1, enchantment.getMinLevel())
                    || !item.canApplyAtEnchantingTable(enchantment)) {
                continue;
            }
            boolean compatible = existing.keySet().stream().allMatch(existingEnchantment ->
                    existingEnchantment == enchantment || existingEnchantment.isCompatibleWith(enchantment));
            if (compatible && offeredIds.add(source.enchantmentId())) {
                applicable.add(source);
            }
        }
        return applicable;
    }

    private static List<BookSource> nearbyEnchantedBooks(Level level, BlockPos tablePos) {
        List<BookSource> books = new ArrayList<>();
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            BlockPos gap = tablePos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
            if (!level.isEmptyBlock(gap)) {
                continue;
            }
            BlockPos shelfPos = tablePos.offset(offset);
            BlockEntity blockEntity = level.getBlockEntity(shelfPos);
            if (!(blockEntity instanceof ChiseledBookShelfBlockEntity shelf)) {
                continue;
            }
            for (int shelfSlot = 0; shelfSlot < shelf.getContainerSize(); shelfSlot++) {
                ItemStack book = shelf.getItem(shelfSlot);
                if (!book.is(Items.ENCHANTED_BOOK) || ArtifactEnchantingSupport.isIdentifiedArtifact(book)) {
                    continue;
                }
                for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(book).entrySet()) {
                    ResourceLocation enchantmentId = BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey());
                    if (enchantmentId != null && entry.getValue() > 0) {
                        books.add(new BookSource(shelfPos, shelfSlot, enchantmentId, entry.getValue()));
                    }
                }
            }
        }
        return books;
    }

    public static void spawnEnchantingParticles(Level level, BlockPos tablePos, RandomSource random) {
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            BlockPos gap = tablePos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
            if (!level.isEmptyBlock(gap)) {
                continue;
            }

            BlockPos shelfPos = tablePos.offset(offset);
            BlockEntity blockEntity = level.getBlockEntity(shelfPos);
            if (!(blockEntity instanceof ChiseledBookShelfBlockEntity shelf)) {
                continue;
            }

            int enchantedSlotMask = ((EnchantedShelfSlotsAccess) shelf).solace$getEnchantedSlotMask();
            List<Integer> enchantedSlots = new ArrayList<>(shelf.getContainerSize());
            for (int shelfSlot = 0; shelfSlot < shelf.getContainerSize(); shelfSlot++) {
                if ((enchantedSlotMask & 1 << shelfSlot) != 0) {
                    enchantedSlots.add(shelfSlot);
                }
            }
            if (enchantedSlots.isEmpty() || random.nextInt(4) != 0) {
                continue;
            }

            int shelfSlot = enchantedSlots.get(random.nextInt(enchantedSlots.size()));
            BlockState shelfState = level.getBlockState(shelfPos);
            Direction facing = shelfState.hasProperty(HorizontalDirectionalBlock.FACING)
                    ? shelfState.getValue(HorizontalDirectionalBlock.FACING)
                    : Direction.getNearest(tablePos.getX() - shelfPos.getX(), 0, tablePos.getZ() - shelfPos.getZ());
            Direction right = facing.getClockWise();

            double columnOffset = (shelfSlot % 3 - 1) * 0.25D;
            double sourceX = shelfPos.getX() + 0.5D + facing.getStepX() * 0.48D
                    + right.getStepX() * columnOffset;
            double sourceY = shelfPos.getY() + (shelfSlot < 3 ? 0.72D : 0.28D);
            double sourceZ = shelfPos.getZ() + 0.5D + facing.getStepZ() * 0.48D
                    + right.getStepZ() * columnOffset;
            double tableX = tablePos.getX() + 0.5D;
            double tableY = tablePos.getY() + 2.0D;
            double tableZ = tablePos.getZ() + 0.5D;

            level.addParticle(ParticleTypes.ENCHANT, tableX, tableY, tableZ,
                    sourceX - tableX, sourceY - tableY, sourceZ - tableZ);
        }
    }

    private static Optional<BookSource> findUpgradeBook(Level level, BlockPos tablePos,
                                                        ResourceLocation enchantmentId) {
        Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(enchantmentId);
        if (enchantment == null) {
            return Optional.empty();
        }
        int baseLevel = Math.max(1, enchantment.getMinLevel());
        return nearbyEnchantedBooks(level, tablePos).stream()
                .filter(source -> source.enchantmentId().equals(enchantmentId) && source.level() == baseLevel)
                .findFirst();
    }

    private static boolean consumeBook(Level level, BookSource source) {
        BlockEntity blockEntity = level.getBlockEntity(source.shelfPosition());
        if (!(blockEntity instanceof ChiseledBookShelfBlockEntity shelf)
                || source.shelfSlot() < 0 || source.shelfSlot() >= shelf.getContainerSize()) {
            return false;
        }
        ItemStack current = shelf.getItem(source.shelfSlot());
        if (!current.is(Items.ENCHANTED_BOOK)
                || EnchantmentHelper.getEnchantments(current).entrySet().stream().noneMatch(entry ->
                source.enchantmentId().equals(BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey()))
                        && entry.getValue() == source.level())) {
            return false;
        }
        shelf.setItem(source.shelfSlot(), new ItemStack(Items.BOOK));
        shelf.setChanged();
        BlockState state = level.getBlockState(source.shelfPosition());
        level.sendBlockUpdated(source.shelfPosition(), state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    private static int bookshelfFingerprint(ItemStack item, List<BookSource> books) {
        int fingerprint = layoutFingerprint(item);
        List<String> entries = books.stream()
                .map(BookSource::fingerprint)
                .sorted()
                .toList();
        return 31 * fingerprint + entries.hashCode();
    }

    private static boolean bookLayoutMatches(ItemStack item, int fingerprint) {
        CompoundTag tag = item.getTag();
        if (tag == null || !tag.contains(BOOK_LAYOUT_TAG, 10)) {
            return false;
        }
        CompoundTag layout = tag.getCompound(BOOK_LAYOUT_TAG);
        return layout.getInt(BOOK_LAYOUT_VERSION_KEY) == BOOK_LAYOUT_VERSION
                && layout.getInt(BOOK_LAYOUT_FINGERPRINT_KEY) == fingerprint;
    }

    private static boolean hasBookLayout(ItemStack item) {
        CompoundTag tag = item.getTag();
        return tag != null && tag.contains(BOOK_LAYOUT_TAG, 10)
                && tag.getCompound(BOOK_LAYOUT_TAG).getInt(BOOK_LAYOUT_VERSION_KEY) == BOOK_LAYOUT_VERSION;
    }

    private static void writeBookLayout(ItemStack item, int fingerprint, CompoundTag sources) {
        CompoundTag layout = new CompoundTag();
        layout.putInt(BOOK_LAYOUT_VERSION_KEY, BOOK_LAYOUT_VERSION);
        layout.putInt(BOOK_LAYOUT_FINGERPRINT_KEY, fingerprint);
        layout.put(BOOK_LAYOUT_CHOICES_KEY, sources);
        item.getOrCreateTag().put(BOOK_LAYOUT_TAG, layout);
    }

    private static Optional<BookSource> readBookSource(ItemStack item, SlotPosition mainPosition,
                                                       SlotPosition choicePosition) {
        CompoundTag tag = item.getTag();
        if (tag == null || !tag.contains(BOOK_LAYOUT_TAG, 10)) {
            return Optional.empty();
        }
        CompoundTag sources = tag.getCompound(BOOK_LAYOUT_TAG).getCompound(BOOK_LAYOUT_CHOICES_KEY);
        String key = sourceKey(mainPosition, choicePosition);
        return sources.contains(key, 10) ? BookSource.fromTag(sources.getCompound(key)) : Optional.empty();
    }

    private static String sourceKey(SlotPosition mainPosition, SlotPosition choicePosition) {
        return mainPosition.ordinal() + "_" + choicePosition.ordinal();
    }

    private static int openChoiceCapacity(ItemStack item) {
        try {
            int locked = chosenStates(mcdeSlots(item)).size();
            return Math.max(0, (SlotPosition.values().length - locked) * SlotPosition.values().length);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    private static boolean canPay(Player player, ItemStack lapis, int lapisCost, int xpCost) {
        boolean hasLapis = lapisCost == 0 || (lapis.is(Items.LAPIS_LAZULI) && lapis.getCount() >= lapisCost);
        return player.isCreative() || (hasLapis && player.experienceLevel >= xpCost);
    }

    private static void pay(Player player, ItemStack lapis, int lapisCost, int xpCost) {
        if (player.isCreative()) {
            return;
        }
        lapis.shrink(lapisCost);
        player.giveExperienceLevels(-xpCost);
    }

    private static MenuProvider menuProvider(String className, String titleKey, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInventory, player) ->
                        createMenu(className, containerId, playerInventory, player, level, pos),
                Component.translatable(titleKey)
        );
    }

    private static AbstractContainerMenu createMenu(String className, int containerId, Inventory playerInventory,
                                                     Player player, Level level, BlockPos pos) {
        try {
            Class<?> handlerClass = Class.forName(className);
            Constructor<?> constructor = handlerClass.getConstructor(int.class, Inventory.class, ContainerLevelAccess.class);
            ContainerLevelAccess context = ContainerLevelAccess.create(level, pos);
            AbstractContainerMenu menu = (AbstractContainerMenu) constructor.newInstance(containerId, playerInventory, context);
            installSlotNormalizer(menu, context, player);
            return menu;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[Solace] Failed to create {}", className, e);
            return null;
        }
    }

    private static boolean stillValidAt(ContainerLevelAccess context, Player player, ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null) {
            return false;
        }
        return AbstractContainerMenuAccessor.invokeStillValid(context, player, block);
    }

    private static int layoutFingerprint(ItemStack stack) {
        int fingerprint = 17;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        fingerprint = 31 * fingerprint + (itemId == null ? 0 : itemId.hashCode());
        fingerprint = 31 * fingerprint + stack.getDamageValue();
        List<String> enchantments = new ArrayList<>();
        EnchantmentHelper.getEnchantments(stack).forEach((enchantment, level) -> {
            ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (id != null) {
                enchantments.add(id + ":" + level);
            }
        });
        Collections.sort(enchantments);
        fingerprint = 31 * fingerprint + enchantments.hashCode();
        return fingerprint;
    }

    private static void invokeWithSlot(Object builder, Object slotPosition, List<ResourceLocation> choices) throws ReflectiveOperationException {
        if (choices.size() >= 3) {
            method(builder, "withSlot", slotPositionClass(), ResourceLocation.class, ResourceLocation.class, ResourceLocation.class)
                    .invoke(builder, slotPosition, choices.get(0), choices.get(1), choices.get(2));
        } else if (choices.size() == 2) {
            method(builder, "withSlot", slotPositionClass(), ResourceLocation.class, ResourceLocation.class)
                    .invoke(builder, slotPosition, choices.get(0), choices.get(1));
        } else {
            method(builder, "withSlot", slotPositionClass(), ResourceLocation.class)
                    .invoke(builder, slotPosition, choices.get(0));
        }
    }

    private static Optional<?> mcdeSlots(ItemStack stack) throws ReflectiveOperationException {
        Class<?> slotsClass = Class.forName("net.backupcup.mcde.util.EnchantmentSlots");
        Method fromItemStack = slotsClass.getMethod("fromItemStack", ItemStack.class);
        return (Optional<?>) fromItemStack.invoke(null, stack);
    }

    private static Class<?> slotPositionClass() throws ClassNotFoundException {
        return Class.forName("net.backupcup.mcde.util.SlotPosition");
    }

    private static int enchantCost(Object enchantmentId, int level) throws ReflectiveOperationException {
        ResourceLocation id = toResourceLocation(enchantmentId);
        Object config = Class.forName("net.backupcup.mcde.MCDEnchantments").getMethod("getConfig").invoke(null);
        return (Integer) config.getClass().getMethod("getEnchantCost", ResourceLocation.class, int.class).invoke(config, id, level);
    }

    private static ResourceLocation toResourceLocation(Object value) {
        return value instanceof ResourceLocation resourceLocation ? resourceLocation : new ResourceLocation(value.toString());
    }

    private static Method method(Object target, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = target.getClass().getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Optional<?> optionalResult(Object target, String name, Class<?> parameterType, Object argument) throws ReflectiveOperationException {
        return (Optional<?>) method(target, name, parameterType).invoke(target, argument);
    }

    private static Optional<?> optionalResult(Object target, String name) throws ReflectiveOperationException {
        return (Optional<?>) method(target, name).invoke(target);
    }

    private static int intResult(Object target, String name) throws ReflectiveOperationException {
        return (Integer) method(target, name).invoke(target);
    }

    private static <T> Optional<T> field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[Solace] Could not read field {} from {}", name, target.getClass().getName(), e);
            return Optional.empty();
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private record ChosenState(ResourceLocation enchantmentId, int level) {
    }

    private record BookSource(BlockPos shelfPosition, int shelfSlot, ResourceLocation enchantmentId, int level) {
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(BOOK_SOURCE_POSITION_KEY, shelfPosition.asLong());
            tag.putInt(BOOK_SOURCE_SLOT_KEY, shelfSlot);
            tag.putString(BOOK_SOURCE_ENCHANTMENT_KEY, enchantmentId.toString());
            tag.putInt(BOOK_SOURCE_LEVEL_KEY, level);
            return tag;
        }

        private String fingerprint() {
            return shelfPosition.asLong() + ":" + shelfSlot + ":" + enchantmentId + ":" + level;
        }

        private static Optional<BookSource> fromTag(CompoundTag tag) {
            try {
                if (!tag.contains(BOOK_SOURCE_POSITION_KEY, 4)
                        || !tag.contains(BOOK_SOURCE_SLOT_KEY, 3)
                        || !tag.contains(BOOK_SOURCE_ENCHANTMENT_KEY, 8)
                        || !tag.contains(BOOK_SOURCE_LEVEL_KEY, 3)) {
                    return Optional.empty();
                }
                return Optional.of(new BookSource(
                        BlockPos.of(tag.getLong(BOOK_SOURCE_POSITION_KEY)),
                        tag.getInt(BOOK_SOURCE_SLOT_KEY),
                        new ResourceLocation(tag.getString(BOOK_SOURCE_ENCHANTMENT_KEY)),
                        tag.getInt(BOOK_SOURCE_LEVEL_KEY)
                ));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }
}
