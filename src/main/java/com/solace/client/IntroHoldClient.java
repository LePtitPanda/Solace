package com.solace.client;

import com.solace.Solace;
import com.solace.client.loading.DimensionLoadingScreenRenderer;
import com.solace.config.DimensionLoadingScreenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client half of {@link com.solace.world.IntroHold}: a loading screen that stays up (and mutes every sound)
 * until the server releases it and the ground under the player has been drawn.
 */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IntroHoldClient {
    private static final int SETTLE_TICKS = 10;
    private static final int FAILSAFE_TICKS = 20 * 90;
    private static boolean held;
    private static boolean released;
    private static boolean readySent;
    private static int readyTicks;
    private static int activeTicks;
    private static int settled;
    private static int age;

    private IntroHoldClient() {
    }

    public static void set(boolean hold) {
        Minecraft minecraft = Minecraft.getInstance();
        if (hold) {
            held = true;
            released = false;
            settled = 0;
            age = 0;
            readySent = false;
            readyTicks = 0;
            activeTicks = 0;
            for (SoundSource source : SoundSource.values()) {
                if (!keeps(source)) minecraft.getSoundManager().stop(null, source);
            }
            minecraft.setScreen(new HoldScreen());
        } else {
            released = true;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !held) return;
        Minecraft minecraft = Minecraft.getInstance();
        // Counted from the ready signal only: while the game is minimized nothing is drawn and ready can't be sent yet.
        if (readySent && ++age > FAILSAFE_TICKS) {
            finish(minecraft);
            return;
        }
        // Tell the server once the ground here (the prison) has been drawn for a second: login is over.
        if (!readySent && minecraft.player != null && minecraft.level != null) {
            BlockPos here = minecraft.player.blockPosition().below();
            boolean loaded = minecraft.level.hasChunkAt(here);
            readyTicks = loaded && minecraft.levelRenderer.isChunkCompiled(here) ? readyTicks + 1 : 0;
            if (loaded && minecraft.isWindowActive()) activeTicks++;
            // isChunkCompiled can stay false (Embeddium); 15 s with the window in front and the ground loaded is enough.
            // Time spent minimized or alt-tabbed doesn't count, so nothing starts while the player is away.
            if (readyTicks >= 20 || activeTicks >= 20 * 15) {
                readySent = true;
                com.solace.quests.network.QuestNetwork.intent(com.solace.quests.network.QuestNetwork.INTRO_READY, "", "", 0, 0, 0);
            }
        }
        if (released && minecraft.player != null && minecraft.level != null) {
            BlockPos below = minecraft.player.blockPosition().below();
            boolean drawn = minecraft.level.hasChunkAt(below) && minecraft.levelRenderer.isChunkCompiled(below);
            settled = drawn ? settled + 1 : 0;
            if (settled >= SETTLE_TICKS) {
                finish(minecraft);
                return;
            }
        }
        // Dimension changes and Escape replace the screen; put it back.
        if (!(minecraft.screen instanceof HoldScreen) && !(minecraft.screen instanceof ReceivingLevelScreen)) {
            minecraft.setScreen(new HoldScreen());
        }
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (held && event.getSound() != null && !keeps(event.getSound().getSource())) event.setSound(null);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        held = false;
    }

    /** Behind the loading screen everything is silent; music only starts once he is in the dream (not the overworld's). */
    public static boolean keeps(SoundSource source) {
        if (source == SoundSource.MASTER) return true;
        Minecraft minecraft = Minecraft.getInstance();
        boolean dream = minecraft.level != null && com.solace.world.DreamDimension.isDream(minecraft.level);
        return dream && (source == SoundSource.MUSIC || source == SoundSource.RECORDS);
    }

    public static boolean muting() {
        return held;
    }

    private static void finish(Minecraft minecraft) {
        held = false;
        if (minecraft.screen instanceof HoldScreen) minecraft.setScreen(null);
    }

    private static final class HoldScreen extends Screen {
        private static final ResourceLocation DREAM = new ResourceLocation(Solace.MOD_ID, "dream");
        private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

        HoldScreen() {
            super(Component.translatable("multiplayer.downloadingTerrain"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            DimensionLoadingScreenConfig.Entry config = DimensionLoadingScreenConfig.get(DREAM);
            if (config == null) config = DimensionLoadingScreenConfig.get(OVERWORLD);
            if (!DimensionLoadingScreenRenderer.render(this, graphics, config, null, getTitle())) {
                graphics.fill(0, 0, width, height, 0xFF000000);
                graphics.drawCenteredString(font, getTitle(), width / 2, height / 2, 0xFFAAAAAA);
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false; // a pause screen would stop the singleplayer server that is sending him to the dream
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }
}
