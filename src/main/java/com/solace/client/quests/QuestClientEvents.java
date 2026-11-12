package com.solace.client.quests;

import com.mojang.blaze3d.platform.InputConstants;
import com.solace.Solace;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/** Client wiring for Solace Quests: keybinds, HUD overlay, beams, cleanup. */
public final class QuestClientEvents {
    private static final KeyMapping OPEN_BOOK = new KeyMapping("key.solace.quests.book", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.solace");
    private static final KeyMapping TOGGLE_HUD = new KeyMapping("key.solace.quests.hud", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.solace");
    private static String lastDimension = "";

    private QuestClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_BOOK);
            event.register(TOGGLE_HUD);
        }

        /** The "Config" button of the mod list (and Configured) opens the tracker position screen. */
        @SubscribeEvent
        public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, parent) -> new QuestHudConfigScreen(parent)));
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("quest_tracker", QuestHud::render);
        }
    }

    @Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || minecraft.player == null) {
                return;
            }
            while (OPEN_BOOK.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new QuestBookScreen());
                }
            }
            while (TOGGLE_HUD.consumeClick()) {
                QuestClientConfig config = QuestClientConfig.get();
                config.hud = !config.hud;
                config.save();
            }
            // Xaero's custom waypoints are not per dimension: rebuild them when the player changes world.
            String dimension = minecraft.player.level().dimension().location().toString();
            if (!dimension.equals(lastDimension)) {
                lastDimension = dimension;
                ClientQuests.refreshWaypoints();
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            lastDimension = "";
            ClientQuests.clear();
        }

        /** Optional: right-clicking the configured item opens the book. */
        @SubscribeEvent
        public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
            String wanted = QuestClientConfig.get().bookItem;
            if (event.getLevel().isClientSide() && !wanted.isEmpty()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
                if (id != null && id.toString().equals(wanted) && Minecraft.getInstance().screen == null) {
                    Minecraft.getInstance().setScreen(new QuestBookScreen());
                }
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            QuestHud.renderBeams(event);
        }
    }
}
