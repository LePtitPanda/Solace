package com.solace.world.gates;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * solace:give_structure_map and solace:give_structure_compass. Searches run off the server thread
 * (like async-locator) through the gate-aware locate, so a closed gate never yields a target.
 * Locator items carry a {@value #TAG} compound; using one searches again.
 */
public final class StructureLocators {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String TAG = "SolaceStructureLocator";
    private static final int DEFAULT_RADIUS = 100;
    private static final int HUM_INTERVAL_TICKS = 40;
    private static final double HUM_RANGE_BLOCKS = 1024;
    private static final ExecutorService SEARCH = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Solace Structure Locator");
        thread.setDaemon(true);
        return thread;
    });

    private StructureLocators() {
    }

    // Effects ---------------------------------------------------------------------------------------

    /** {@code options}: the effect's JSON (name, lore, decoration, search_radius / item, sound). */
    public static void giveMap(ServerPlayer player, String structure, String options) {
        JsonObject json = json(options);
        ItemStack placeholder = new ItemStack(Items.MAP);
        style(placeholder, json);
        CompoundTag locator = new CompoundTag();
        locator.putString("kind", "map");
        locator.putString("structure", structure);
        locator.putInt("radius", integer(json, "search_radius", DEFAULT_RADIUS));
        locator.putString("decoration", string(json, "decoration", "red_x"));
        placeholder.getOrCreateTag().put(TAG, locator);
        search(player, placeholder, pos -> ItemHandlerHelper.giveItemToPlayer(player,
                pos == null ? unsettled(player, placeholder) : filledMap(player.serverLevel(), placeholder, pos)));
    }

    public static void giveCompass(ServerPlayer player, String structure, Item item, String options) {
        JsonObject json = json(options);
        ItemStack compass = new ItemStack(item);
        style(compass, json);
        CompoundTag locator = new CompoundTag();
        locator.putString("kind", "compass");
        locator.putString("structure", structure);
        locator.putInt("radius", integer(json, "search_radius", DEFAULT_RADIUS));
        String sound = string(json, "sound", null);
        if (sound != null) {
            locator.putString("sound", sound);
        }
        compass.getOrCreateTag().put(TAG, locator);
        search(player, compass, pos -> {
            point(player.serverLevel(), compass, pos);
            if (pos == null) {
                player.displayClientMessage(Component.translatableWithFallback("solace.structure_compass.lost",
                        "The needle wanders. Use it again later."), true);
            }
            ItemHandlerHelper.giveItemToPlayer(player, compass);
        });
    }

    // Item use -------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        CompoundTag locator = stack.getTagElement(TAG);
        if (locator == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getCooldowns().addCooldown(stack.getItem(), 40);
        InteractionHand hand = event.getHand();
        if (locator.getString("kind").equals("map")) {
            search(player, stack, pos -> {
                if (pos == null) {
                    unsettledMessage(player);
                } else if (player.getItemInHand(hand) == stack && !stack.isEmpty()) {
                    ItemStack map = filledMap(player.serverLevel(), stack, pos);
                    stack.shrink(1);
                    ItemHandlerHelper.giveItemToPlayer(player, map);
                }
            });
        } else {
            search(player, stack, pos -> {
                point(player.serverLevel(), stack, pos);
                if (pos == null) {
                    player.displayClientMessage(Component.translatableWithFallback("solace.structure_compass.lost",
                            "The needle wanders. Use it again later."), true);
                } else {
                    hum(player, stack);
                }
            });
        }
    }

    /** Locator compasses with a sound hum while held, louder the closer the target is. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % HUM_INTERVAL_TICKS != 0) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (hum(player, player.getItemInHand(hand))) {
                return;
            }
        }
    }

    private static boolean hum(ServerPlayer player, ItemStack stack) {
        CompoundTag locator = stack.getTagElement(TAG);
        CompoundTag tag = stack.getTag();
        if (locator == null || !locator.contains("sound") || tag == null || !tag.contains("LodestonePos")
                || !player.level().dimension().location().toString().equals(tag.getString("LodestoneDimension"))) {
            return false;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(locator.getString("sound"));
        if (soundId == null) {
            return false;
        }
        BlockPos target = NbtUtils.readBlockPos(tag.getCompound("LodestonePos"));
        double distance = Math.sqrt(Mth.square(player.getX() - target.getX()) + Mth.square(player.getZ() - target.getZ()));
        float closeness = (float) Mth.clamp(1 - distance / HUM_RANGE_BLOCKS, 0, 1);
        player.playNotifySound(SoundEvent.createVariableRangeEvent(soundId), SoundSource.PLAYERS,
                0.15F + 0.85F * closeness, 0.8F + 0.6F * closeness);
        return true;
    }

    // Helpers --------------------------------------------------------------------------------------

    /** Searches off-thread, then runs {@code then} on the server thread if the player is still online. */
    private static void search(ServerPlayer player, ItemStack stack, Consumer<BlockPos> then) {
        CompoundTag locator = stack.getTagElement(TAG);
        String structure = locator.getString("structure");
        int radius = locator.contains("radius") ? locator.getInt("radius") : DEFAULT_RADIUS;
        ServerLevel level = player.serverLevel();
        BlockPos from = player.blockPosition();
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        CompletableFuture.supplyAsync(() -> StructureGates.find(level, structure, from, radius, true), SEARCH)
                .whenCompleteAsync((pos, error) -> {
                    if (error != null) {
                        LOGGER.error("[Solace] Structure search for {} failed", structure, error);
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(id);
                    if (online != null) {
                        then.accept(error == null ? pos : null);
                    }
                }, server);
    }

    private static ItemStack filledMap(ServerLevel level, ItemStack placeholder, BlockPos pos) {
        CompoundTag locator = placeholder.getTagElement(TAG);
        ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, pos, "+", decoration(locator.getString("decoration")));
        CompoundTag display = placeholder.getTagElement("display");
        if (display != null) {
            map.getOrCreateTag().put("display", display.copy());
        }
        return map;
    }

    private static ItemStack unsettled(ServerPlayer player, ItemStack placeholder) {
        unsettledMessage(player);
        return placeholder;
    }

    private static void unsettledMessage(ServerPlayer player) {
        player.displayClientMessage(Component.translatableWithFallback("solace.structure_map.unsettled",
                "The ink won't settle. Use the map again later, or somewhere else."), true);
    }

    /** Lodestone-style target; no position (dimension only) makes the needle spin. */
    private static void point(ServerLevel level, ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, level.dimension()).result()
                .ifPresent(dimension -> tag.put("LodestoneDimension", dimension));
        tag.putBoolean("LodestoneTracked", false);
        if (pos == null) {
            tag.remove("LodestonePos");
        } else {
            tag.put("LodestonePos", NbtUtils.writeBlockPos(pos));
        }
    }

    static MapDecoration.Type decoration(String name) {
        try {
            return MapDecoration.Type.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return MapDecoration.Type.RED_X;
        }
    }

    private static void style(ItemStack stack, JsonObject json) {
        JsonElement name = json.get("name");
        if (name != null) {
            Component component = Component.Serializer.fromJson(name);
            if (component != null) {
                stack.setHoverName(component);
            }
        }
        JsonElement lore = json.get("lore");
        if (lore != null) {
            List<JsonElement> lines = new ArrayList<>();
            if (lore.isJsonArray()) {
                lore.getAsJsonArray().forEach(lines::add);
            } else {
                lines.add(lore);
            }
            ListTag list = new ListTag();
            for (JsonElement line : lines) {
                Component component = Component.Serializer.fromJson(line);
                if (component != null) {
                    list.add(StringTag.valueOf(Component.Serializer.toJson(component)));
                }
            }
            stack.getOrCreateTagElement("display").put("Lore", list);
        }
    }

    private static JsonObject json(String text) {
        try {
            JsonElement element = text == null ? null : JsonParser.parseString(text);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException exception) {
            return new JsonObject();
        }
    }

    private static int integer(JsonObject json, String key, int def) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : def;
    }

    @Nullable
    private static String string(JsonObject json, String key, @Nullable String def) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : def;
    }
}
