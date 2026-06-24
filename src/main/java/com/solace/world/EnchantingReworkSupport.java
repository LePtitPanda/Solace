package com.solace.world;

import com.mojang.logging.LogUtils;
import com.solace.config.EnchantingReworkConfig;
import com.solace.mixin.AbstractContainerMenuAccessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class EnchantingReworkSupport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation RUNIC_TABLE = id("mcde", "runic_table");
    private static final ResourceLocation ROLL_BENCH = id("mcde", "roll_bench");
    private static final ResourceLocation ATONING_TABLE = id("caverns_and_chasms", "atoning_table");
    private static final String SOLACE_LAYOUT_TAG = "SolaceEnchantingLayout";
    private static final String SOLACE_LAYOUT_POWER_KEY = "Power";
    private static final String SOLACE_LAYOUT_CHOICES_KEY = "Choices";
    private static final String SOLACE_LAYOUT_FINGERPRINT_KEY = "Fingerprint";
    private static final String SOLACE_LAYOUT_VERSION_KEY = "Version";
    private static final int SOLACE_LAYOUT_VERSION = 2;

    private EnchantingReworkSupport() {
    }

    public static MenuProvider runicTableProvider(Level level, BlockPos pos) {
        return menuProvider("net.backupcup.mcde.screen.handler.RunicTableScreenHandler", "block.mcde.runic_table", level, pos);
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

    public static void installSlotNormalizer(AbstractContainerMenu menu, ContainerLevelAccess context) {
        menu.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack stack) {
                Optional<Container> inventory = inventory(handler);
                if (slotId == 0 && inventory.isPresent()) {
                    ItemStack actualStack = inventory.get().getItem(0);
                    if (normalizeSlotStack(actualStack, context)) {
                        inventory.get().setChanged();
                        scheduleHandlerSync(handler, context);
                    }
                }
            }

            @Override
            public void dataChanged(AbstractContainerMenu handler, int property, int value) {
            }
        });
    }

    public static boolean normalizeHandlerStack(Object handler) {
        return normalizeHandlerStack(handler, false);
    }

    public static boolean normalizeHandlerStack(Object handler, boolean force) {
        Optional<ContainerLevelAccess> context = context(handler);
        Optional<Container> inventory = inventory(handler);
        if (context.isEmpty() || inventory.isEmpty()) {
            return false;
        }

        ItemStack stack = inventory.get().getItem(0);
        if (!normalizeSlotStack(stack, context.get(), force)) {
            return false;
        }

        inventory.get().setChanged();
        return true;
    }

    public static void synchronizeHandlerInventory(Object handler) {
        inventory(handler).ifPresent(Container::setChanged);
        if (handler instanceof AbstractContainerMenu menu) {
            menu.broadcastFullState();
        }
    }

    private static void scheduleHandlerSync(AbstractContainerMenu handler, ContainerLevelAccess context) {
        context.evaluate((level, pos) -> {
            if (!level.isClientSide() && level.getServer() != null) {
                level.getServer().execute(handler::broadcastFullState);
            }
            return true;
        }, false);
    }

    public static boolean stillValidAtEnchantingTable(Object handler, Player player) {
        Optional<ContainerLevelAccess> context = context(handler);
        return context.isPresent()
                && (AbstractContainerMenuAccessor.invokeStillValid(context.get(), player, net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)
                || stillValidAt(context.get(), player, RUNIC_TABLE));
    }

    public static boolean stillValidAtAtoningTable(Object handler, Player player) {
        Optional<ContainerLevelAccess> context = context(handler);
        return context.isPresent()
                && (stillValidAt(context.get(), player, ATONING_TABLE)
                || stillValidAt(context.get(), player, ROLL_BENCH));
    }

    public static boolean hasEnchantingPower(ContainerLevelAccess context, int requiredPower) {
        return bookshelfPower(context) >= requiredPower;
    }

    public static int bookshelfPower(ContainerLevelAccess context) {
        return context.evaluate(EnchantingReworkSupport::bookshelfPower, 0);
    }

    public static int xpCostReduction(ContainerLevelAccess context, int baseCost) {
        if (baseCost <= 0) {
            return 0;
        }

        int discount = context.evaluate(EnchantingReworkSupport::discountShelfCount, 0)
                * EnchantingReworkConfig.xpCostReductionPerShelf();
        discount = Math.min(discount, EnchantingReworkConfig.maxXpCostReduction());
        return Math.min(discount, Math.max(0, baseCost - 1));
    }

    public static boolean normalizeSlotStack(ItemStack stack, ContainerLevelAccess context) {
        return normalizeSlotStack(stack, context, false);
    }

    public static boolean normalizeSlotStack(ItemStack stack, ContainerLevelAccess context, boolean force) {
        if (stack.isEmpty()) {
            return false;
        }

        int power = bookshelfPower(context);
        int[] targetChoices = EnchantingReworkConfig.slotChoicesForPower(power);
        int fingerprint = layoutFingerprint(stack);
        try {
            Optional<?> currentSlots = mcdeSlots(stack);
            if (!force && currentSlots.isPresent() && slotChoicesMatch(currentSlots.get(), targetChoices)) {
                if (layoutMarkerMatches(stack, power, targetChoices, fingerprint)) {
                    return false;
                }
                writeLayoutMarker(stack, power, targetChoices, fingerprint);
                return true;
            }

            Object replacementSlots = buildSlots(stack, currentSlots, targetChoices, layoutSeed(fingerprint), layoutMarkerTrusted(stack, fingerprint));
            if (replacementSlots == null) {
                return false;
            }

            method(replacementSlots, "updateItemStack", ItemStack.class).invoke(replacementSlots, stack);
            writeLayoutMarker(stack, power, targetChoices, layoutFingerprint(stack));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not normalize MCDE enchantment slots", e);
            return false;
        }
    }

    public static Optional<RunicClick> inspectRunicClick(Object handler, int buttonId) {
        if (buttonId < 0) {
            return Optional.empty();
        }

        Optional<Container> inventory = inventory(handler);
        if (inventory.isEmpty()) {
            return Optional.empty();
        }

        try {
            ItemStack stack = inventory.get().getItem(0);
            Optional<?> slots = mcdeSlots(stack);
            if (slots.isEmpty()) {
                return Optional.empty();
            }

            Object[] positions = slotPositions();
            int slotIndex = buttonId / positions.length;
            int choiceIndex = buttonId % positions.length;
            if (slotIndex < 0 || slotIndex >= positions.length) {
                return Optional.empty();
            }

            Optional<?> clickedSlot = optionalResult(slots.get(), "getEnchantmentSlot", slotPositionClass(), positions[slotIndex]);
            if (clickedSlot.isEmpty()) {
                return Optional.empty();
            }

            Object slot = clickedSlot.get();
            boolean maxedOut = booleanResult(slot, "isMaxedOut");
            Optional<?> chosen = optionalResult(slot, "getChosen");
            int level;
            Object enchantmentId;
            if (chosen.isPresent()) {
                if (maxedOut) {
                    return Optional.empty();
                }
                level = intResult(slot, "getLevel") + 1;
                enchantmentId = method(chosen.get(), "getEnchantmentId").invoke(chosen.get());
            } else {
                if (choiceIndex < 0 || choiceIndex >= positions.length) {
                    return Optional.empty();
                }
                Optional<?> choice = optionalResult(slot, "getChoice", slotPositionClass(), positions[choiceIndex]);
                if (choice.isEmpty()) {
                    return Optional.empty();
                }
                level = 1;
                enchantmentId = choice.get();
            }

            int baseCost = enchantCost(enchantmentId, level);
            return Optional.of(new RunicClick(level, baseCost));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not inspect MCDE runic table click", e);
            return Optional.empty();
        }
    }

    public static Optional<RerollClick> inspectRerollClick(Object handler, int buttonId) {
        if (buttonId == -1) {
            return Optional.of(new RerollClick(1));
        }
        if (buttonId < 0) {
            return Optional.empty();
        }

        Optional<Container> inventory = inventory(handler);
        if (inventory.isEmpty()) {
            return Optional.empty();
        }

        try {
            ItemStack stack = inventory.get().getItem(0);
            Optional<?> slots = mcdeSlots(stack);
            if (slots.isEmpty()) {
                return Optional.empty();
            }

            Object[] positions = slotPositions();
            int slotIndex = buttonId / positions.length;
            int choiceIndex = buttonId % positions.length;
            if (slotIndex < 0 || slotIndex >= positions.length || choiceIndex < 0 || choiceIndex >= positions.length) {
                return Optional.empty();
            }

            Optional<?> clickedSlot = optionalResult(slots.get(), "getEnchantmentSlot", slotPositionClass(), positions[slotIndex]);
            if (clickedSlot.isEmpty()) {
                return Optional.empty();
            }

            Object slot = clickedSlot.get();
            Optional<?> chosen = optionalResult(slot, "getChosen");
            Object enchantmentId;
            if (chosen.isPresent()) {
                enchantmentId = method(chosen.get(), "getEnchantmentId").invoke(chosen.get());
            } else {
                Optional<?> choice = optionalResult(slot, "getChoice", slotPositionClass(), positions[choiceIndex]);
                if (choice.isEmpty()) {
                    return Optional.empty();
                }
                enchantmentId = choice.get();
            }

            int baseCost = intResult(slots.get(), "getNextRerollCost", enchantmentId);
            return Optional.of(new RerollClick(baseCost));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not inspect MCDE roll bench click", e);
            return Optional.empty();
        }
    }

    private static MenuProvider menuProvider(String className, String titleKey, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInventory, player) -> createMenu(className, containerId, playerInventory, level, pos),
                Component.translatable(titleKey)
        );
    }

    private static AbstractContainerMenu createMenu(String className, int containerId, Inventory playerInventory, Level level, BlockPos pos) {
        try {
            Class<?> handlerClass = Class.forName(className);
            Constructor<?> constructor = handlerClass.getConstructor(int.class, Inventory.class, ContainerLevelAccess.class);
            ContainerLevelAccess context = ContainerLevelAccess.create(level, pos);
            AbstractContainerMenu menu = (AbstractContainerMenu) constructor.newInstance(containerId, playerInventory, context);
            installSlotNormalizer(menu, context);
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

    private static int bookshelfPower(Level level, BlockPos tablePos) {
        float power = 0.0F;
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantmentTableBlock.isValidBookShelf(level, tablePos, offset)) {
                BlockPos shelfPos = tablePos.offset(offset);
                BlockState shelfState = level.getBlockState(shelfPos);
                power += shelfState.getEnchantPowerBonus(level, shelfPos);
            }
        }
        return Math.round(power);
    }

    private static int discountShelfCount(Level level, BlockPos tablePos) {
        List<ResourceLocation> discountShelves = EnchantingReworkConfig.xpCostReductionBookshelves();
        if (discountShelves.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (!EnchantmentTableBlock.isValidBookShelf(level, tablePos, offset)) {
                continue;
            }

            BlockPos shelfPos = tablePos.offset(offset);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(shelfPos).getBlock());
            if (discountShelves.contains(blockId)) {
                count++;
            }
        }
        return count;
    }

    private static Object buildSlots(ItemStack stack, Optional<?> currentSlots, int[] targetChoices, long seed, boolean preserveCurrentChoices) throws ReflectiveOperationException {
        List<ResourceLocation> candidates = enchantmentCandidates(stack, seed);
        Object builder = Class.forName("net.backupcup.mcde.util.EnchantmentSlots").getMethod("builder").invoke(null);
        Object[] positions = slotPositions();
        List<ResourceLocation> selected = new ArrayList<>();
        boolean addedAnySlot = false;

        for (int slot = 0; slot < Math.min(targetChoices.length, positions.length); slot++) {
            int choiceCount = Math.min(3, Math.max(0, targetChoices[slot]));
            Optional<?> currentSlot = currentSlots.isPresent()
                    ? optionalResult(currentSlots.get(), "getEnchantmentSlot", slotPositionClass(), positions[slot])
                    : Optional.empty();
            List<ResourceLocation> choices = chosenSlotChoices(currentSlot);
            if (choices.isEmpty() && preserveCurrentChoices) {
                choices = currentSlotChoices(currentSlot, choiceCount);
            }
            if (choices.isEmpty()) {
                choices = takeChoices(candidates, selected, choiceCount);
            } else if (choices.size() < choiceCount) {
                choices = new ArrayList<>(choices);
                List<ResourceLocation> alreadyPresent = new ArrayList<>(selected);
                alreadyPresent.addAll(choices);
                choices.addAll(takeChoices(candidates, alreadyPresent, choiceCount - choices.size()));
            }
            if (choices.isEmpty()) {
                continue;
            }

            invokeWithSlot(builder, positions[slot], choices);
            selected.addAll(choices);
            addedAnySlot = true;
        }

        if (!addedAnySlot) {
            return null;
        }

        Object built = method(builder, "build").invoke(builder);
        if (currentSlots.isPresent()) {
            copyRerollState(currentSlots.get(), built);
            copyChosenState(currentSlots.get(), built);
        }
        return built;
    }

    private static List<ResourceLocation> currentSlotChoices(Optional<?> currentSlot, int choiceCount) throws ReflectiveOperationException {
        if (currentSlot.isEmpty() || choiceCount <= 0) {
            return List.of();
        }

        List<ResourceLocation> choices = new ArrayList<>();
        for (Object position : slotPositions()) {
            if (choices.size() >= choiceCount) {
                break;
            }
            Optional<?> choice = optionalResult(currentSlot.get(), "getChoice", slotPositionClass(), position);
            if (choice.isPresent()) {
                choices.add(toResourceLocation(choice.get()));
            }
        }
        return choices;
    }

    private static List<ResourceLocation> chosenSlotChoices(Optional<?> currentSlot) throws ReflectiveOperationException {
        if (currentSlot.isEmpty() || optionalResult(currentSlot.get(), "getChosen").isEmpty()) {
            return List.of();
        }

        List<ResourceLocation> choices = new ArrayList<>();
        for (Object position : slotPositions()) {
            Optional<?> choice = optionalResult(currentSlot.get(), "getChoice", slotPositionClass(), position);
            if (choice.isPresent()) {
                choices.add(toResourceLocation(choice.get()));
            }
        }
        return choices;
    }

    private static List<ResourceLocation> enchantmentCandidates(ItemStack stack, long seed) throws ReflectiveOperationException {
        Class<?> utilsClass = Class.forName("net.backupcup.mcde.util.EnchantmentUtils");
        @SuppressWarnings("unchecked")
        Stream<ResourceLocation> stream = (Stream<ResourceLocation>) utilsClass.getMethod("getAllEnchantmentsForItem", ItemStack.class).invoke(null, stack);
        List<ResourceLocation> candidates = stream.collect(Collectors.toCollection(ArrayList::new));
        Set<ResourceLocation> existing = existingEnchantments(stack);
        candidates.removeIf(existing::contains);
        Collections.shuffle(candidates, new Random(seed));
        return candidates;
    }

    private static boolean layoutMarkerMatches(ItemStack stack, int power, int[] targetChoices, int fingerprint) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(SOLACE_LAYOUT_TAG, 10)) {
            return false;
        }

        CompoundTag layout = tag.getCompound(SOLACE_LAYOUT_TAG);
        return layout.getInt(SOLACE_LAYOUT_VERSION_KEY) == SOLACE_LAYOUT_VERSION
                && layout.getInt(SOLACE_LAYOUT_POWER_KEY) == power
                && layout.getInt(SOLACE_LAYOUT_FINGERPRINT_KEY) == fingerprint
                && Arrays.equals(layout.getIntArray(SOLACE_LAYOUT_CHOICES_KEY), normalizedChoices(targetChoices));
    }

    private static boolean layoutMarkerTrusted(ItemStack stack, int fingerprint) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(SOLACE_LAYOUT_TAG, 10)) {
            return false;
        }

        CompoundTag layout = tag.getCompound(SOLACE_LAYOUT_TAG);
        return layout.getInt(SOLACE_LAYOUT_VERSION_KEY) == SOLACE_LAYOUT_VERSION
                && layout.getInt(SOLACE_LAYOUT_FINGERPRINT_KEY) == fingerprint;
    }

    private static void writeLayoutMarker(ItemStack stack, int power, int[] targetChoices, int fingerprint) {
        CompoundTag layout = new CompoundTag();
        layout.putInt(SOLACE_LAYOUT_VERSION_KEY, SOLACE_LAYOUT_VERSION);
        layout.putInt(SOLACE_LAYOUT_POWER_KEY, power);
        layout.putIntArray(SOLACE_LAYOUT_CHOICES_KEY, normalizedChoices(targetChoices));
        layout.putInt(SOLACE_LAYOUT_FINGERPRINT_KEY, fingerprint);
        stack.getOrCreateTag().put(SOLACE_LAYOUT_TAG, layout);
    }

    private static int[] normalizedChoices(int[] targetChoices) {
        int[] choices = {0, 0, 0};
        for (int i = 0; i < choices.length && i < targetChoices.length; i++) {
            choices[i] = Math.max(0, Math.min(3, targetChoices[i]));
        }
        return choices;
    }

    private static long layoutSeed(int fingerprint) {
        return 0x5DEECE66DL * 31 + fingerprint;
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

    private static Set<ResourceLocation> existingEnchantments(ItemStack stack) {
        Set<ResourceLocation> existing = new HashSet<>();
        for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
            ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
            if (id != null) {
                existing.add(id);
            }
        }
        return existing;
    }

    private static List<ResourceLocation> takeChoices(List<ResourceLocation> candidates, List<ResourceLocation> selected, int count) throws ReflectiveOperationException {
        List<ResourceLocation> choices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation choice = takeCompatibleChoice(candidates, selected, choices);
            if (choice == null) {
                choice = takeAnyChoice(candidates, selected, choices);
            }
            if (choice == null) {
                break;
            }
            choices.add(choice);
        }
        return choices;
    }

    private static ResourceLocation takeCompatibleChoice(List<ResourceLocation> candidates, List<ResourceLocation> selected, List<ResourceLocation> choices) throws ReflectiveOperationException {
        List<ResourceLocation> present = new ArrayList<>(selected);
        present.addAll(choices);
        for (ResourceLocation candidate : candidates) {
            if (!present.contains(candidate) && isCompatible(present, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static ResourceLocation takeAnyChoice(List<ResourceLocation> candidates, List<ResourceLocation> selected, List<ResourceLocation> choices) {
        for (ResourceLocation candidate : candidates) {
            if (!selected.contains(candidate) && !choices.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isCompatible(Collection<ResourceLocation> present, ResourceLocation candidate) throws ReflectiveOperationException {
        if (present.isEmpty()) {
            return true;
        }
        Class<?> utilsClass = Class.forName("net.backupcup.mcde.util.EnchantmentUtils");
        return (Boolean) utilsClass.getMethod("isCompatible", Collection.class, ResourceLocation.class).invoke(null, present, candidate);
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

    private static void copyRerollState(Object sourceSlots, Object targetSlots) throws ReflectiveOperationException {
        int nextRerollCost = intResult(sourceSlots, "getNextRerollCost");
        int nextPowerfulRerollCost = intResult(sourceSlots, "getNextRerollCostPowerful");
        method(targetSlots, "setNextRerollCost", int.class).invoke(targetSlots, nextRerollCost);
        method(targetSlots, "setNextRerollCostPowerful", int.class).invoke(targetSlots, nextPowerfulRerollCost);

        @SuppressWarnings("unchecked")
        Set<ResourceLocation> gilding = (Set<ResourceLocation>) method(sourceSlots, "getGildingIds").invoke(sourceSlots);
        method(targetSlots, "addAllGilding", Collection.class).invoke(targetSlots, gilding);
    }

    private static void copyChosenState(Object sourceSlots, Object targetSlots) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        Stream<Object> stream = (Stream<Object>) method(sourceSlots, "stream").invoke(sourceSlots);
        for (Object sourceSlot : stream.toList()) {
            Optional<?> chosen = optionalResult(sourceSlot, "getChosen");
            if (chosen.isEmpty()) {
                continue;
            }

            Object slotPosition = method(sourceSlot, "getSlotPosition").invoke(sourceSlot);
            Optional<?> targetSlot = optionalResult(targetSlots, "getEnchantmentSlot", slotPositionClass(), slotPosition);
            if (targetSlot.isEmpty()) {
                continue;
            }

            Object choicePosition = method(chosen.get(), "getChoicePosition").invoke(chosen.get());
            int level = intResult(chosen.get(), "getLevel");
            method(targetSlot.get(), "setChosen", slotPositionClass(), int.class).invoke(targetSlot.get(), choicePosition, level);
        }
    }

    private static boolean slotChoicesMatch(Object slots, int[] targetChoices) throws ReflectiveOperationException {
        int[] actualChoices = new int[]{0, 0, 0};
        @SuppressWarnings("unchecked")
        Stream<Object> stream = (Stream<Object>) method(slots, "stream").invoke(slots);
        for (Object slot : stream.toList()) {
            Object slotPosition = method(slot, "getSlotPosition").invoke(slot);
            int slotIndex = ((Enum<?>) slotPosition).ordinal();
            if (slotIndex < actualChoices.length) {
                List<?> choices = (List<?>) method(slot, "choices").invoke(slot);
                actualChoices[slotIndex] = choices.size();
            }
        }

        for (int i = 0; i < actualChoices.length; i++) {
            if (actualChoices[i] != Math.max(0, Math.min(3, targetChoices[i]))) {
                return false;
            }
        }
        return true;
    }

    private static Optional<?> mcdeSlots(ItemStack stack) throws ReflectiveOperationException {
        Class<?> slotsClass = Class.forName("net.backupcup.mcde.util.EnchantmentSlots");
        Method fromItemStack = slotsClass.getMethod("fromItemStack", ItemStack.class);
        return (Optional<?>) fromItemStack.invoke(null, stack);
    }

    private static Object[] slotPositions() throws ReflectiveOperationException {
        return (Object[]) slotPositionClass().getMethod("values").invoke(null);
    }

    private static Class<?> slotPositionClass() throws ClassNotFoundException {
        return Class.forName("net.backupcup.mcde.util.SlotPosition");
    }

    private static int enchantCost(Object enchantmentId, int level) throws ReflectiveOperationException {
        ResourceLocation id = toResourceLocation(enchantmentId);
        Object config = Class.forName("net.backupcup.mcde.MCDEnchantments").getMethod("getConfig").invoke(null);
        return (Integer) config.getClass().getMethod("getEnchantCost", ResourceLocation.class, int.class).invoke(config, id, level);
    }

    public static int rerollCost(Object slots, Object enchantmentId) {
        try {
            return intResult(slots, "getNextRerollCost", enchantmentId);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not inspect MCDE reroll cost", e);
            return 1;
        }
    }

    public static Optional<ResourceLocation> fallbackRerollChoice(Object handler, Object clickedSlotPosition) {
        Optional<Container> inventory = inventory(handler);
        if (inventory.isEmpty()) {
            return Optional.empty();
        }

        try {
            ItemStack stack = inventory.get().getItem(0);
            Optional<?> slots = mcdeSlots(stack);
            if (slots.isEmpty()) {
                return Optional.empty();
            }

            Optional<?> clickedSlot = optionalResult(slots.get(), "getEnchantmentSlot", slotPositionClass(), clickedSlotPosition);
            if (clickedSlot.isEmpty()) {
                return Optional.empty();
            }

            Set<ResourceLocation> currentClickedChoices = new HashSet<>();
            for (Object choice : (List<?>) method(clickedSlot.get(), "choices").invoke(clickedSlot.get())) {
                Object enchantmentId = method(choice, "getEnchantmentId").invoke(choice);
                currentClickedChoices.add(toResourceLocation(enchantmentId));
            }
            List<ResourceLocation> protectedChoices = otherSlotChoices(slots.get(), clickedSlotPosition);

            Class<?> utilsClass = Class.forName("net.backupcup.mcde.util.EnchantmentUtils");
            @SuppressWarnings("unchecked")
            Stream<ResourceLocation> stream = (Stream<ResourceLocation>) utilsClass.getMethod("getEnchantmentsNotInItem", ItemStack.class).invoke(null, stack);
            List<ResourceLocation> candidates = stream
                    .filter(candidate -> !currentClickedChoices.contains(candidate))
                    .filter(candidate -> !protectedChoices.contains(candidate))
                    .collect(Collectors.toCollection(ArrayList::new));
            List<ResourceLocation> compatibleCandidates = new ArrayList<>();
            for (ResourceLocation candidate : candidates) {
                if (isCompatible(protectedChoices, candidate)) {
                    compatibleCandidates.add(candidate);
                }
            }
            if (!compatibleCandidates.isEmpty()) {
                candidates = compatibleCandidates;
            }
            Collections.shuffle(candidates, new Random(System.nanoTime() ^ layoutFingerprint(stack) ^ ((Enum<?>) clickedSlotPosition).ordinal()));
            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            Optional<ResourceLocation> generated = (Optional<ResourceLocation>) utilsClass
                    .getMethod("generateEnchantment", ItemStack.class, Optional.class, List.class)
                    .invoke(null, stack, Optional.empty(), candidates);
            return generated.isPresent() ? generated : Optional.of(candidates.get(0));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("[Solace] Could not generate fallback MCDE reroll choice", e);
            return Optional.empty();
        }
    }

    private static List<ResourceLocation> otherSlotChoices(Object slots, Object clickedSlotPosition) throws ReflectiveOperationException {
        List<ResourceLocation> choices = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Stream<Object> stream = (Stream<Object>) method(slots, "stream").invoke(slots);
        for (Object slot : stream.toList()) {
            Object slotPosition = method(slot, "getSlotPosition").invoke(slot);
            if (slotPosition.equals(clickedSlotPosition)) {
                continue;
            }

            for (Object choice : (List<?>) method(slot, "choices").invoke(slot)) {
                Object enchantmentId = method(choice, "getEnchantmentId").invoke(choice);
                choices.add(toResourceLocation(enchantmentId));
            }
        }
        return choices;
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

    private static boolean booleanResult(Object target, String name) throws ReflectiveOperationException {
        return (Boolean) method(target, name).invoke(target);
    }

    private static int intResult(Object target, String name) throws ReflectiveOperationException {
        return (Integer) method(target, name).invoke(target);
    }

    private static int intResult(Object target, String name, Object argument) throws ReflectiveOperationException {
        return (Integer) method(target, name, argument.getClass()).invoke(target, argument);
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

    public record RunicClick(int level, int baseCost) {
    }

    public record RerollClick(int baseCost) {
    }
}
