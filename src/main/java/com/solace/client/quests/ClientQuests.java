package com.solace.client.quests;

import com.mojang.logging.LogUtils;
import com.solace.quests.network.QuestNetwork;
import org.slf4j.Logger;
import com.solace.quests.network.Views.ChapterView;
import com.solace.quests.network.Views.FactionView;
import com.solace.quests.network.Views.MarkerView;
import com.solace.quests.network.Views.QuestView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Client mirror of what the server told this player, and the entry points for incoming packets. */
public final class ClientQuests {
    public static List<ChapterView> chapters = List.of();
    public static List<FactionView> factions = List.of();
    public static List<Component> log = List.of();
    public static List<MarkerView> markers = List.of();
    public static final Map<ResourceLocation, QuestView> QUESTS = new TreeMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean xaeroBroken;

    private ClientQuests() {
    }

    public static void meta(QuestNetwork.Meta meta) {
        List<ChapterView> sorted = new ArrayList<>(meta.chapters());
        sorted.sort(Comparator.comparingInt(ChapterView::order));
        chapters = sorted;
        factions = meta.factions();
        log = meta.log();
    }

    public static void quests(QuestNetwork.Quests packet) {
        if (packet.reset()) {
            QUESTS.clear();
        }
        packet.removed().forEach(QUESTS::remove);
        packet.updates().forEach(view -> QUESTS.put(view.id(), view));
    }

    public static void markers(List<MarkerView> list) {
        markers = list;
        refreshWaypoints();
    }

    /** Pushes the markers of the current dimension to Xaero's Minimap, when it is installed. */
    public static void refreshWaypoints() {
        if (!xaeroBroken && ModList.get().isLoaded("xaerominimap") && QuestClientConfig.get().xaeroWaypoints) {
            try {
                XaeroMarkerCompat.update(markers);
            } catch (RuntimeException | LinkageError exception) {
                // A different Xaero version must never take the quest UI down with it.
                xaeroBroken = true;
                LOGGER.warn("[Solace Quests] Xaero's Minimap waypoints disabled: this Xaero version is not "
                        + "compatible. The HUD compass still works.", exception);
            }
        }
    }

    private static boolean fancyBroken;

    public static void toast(QuestNetwork.Toast toast) {
        if (QuestClientConfig.get().toasts) {
            // A finished quest goes to Fancy Toasts when that mod is installed.
            if (toast.kind().equals("completed") && !fancyBroken && ModList.get().isLoaded("fancytoasts")) {
                try {
                    FancyToastCompat.show(toast);
                    return;
                } catch (RuntimeException | LinkageError exception) {
                    fancyBroken = true;
                    LOGGER.warn("[Solace Quests] Fancy Toasts is not compatible; using the built-in toast.", exception);
                }
            }
            Minecraft.getInstance().getToasts().addToast(new QuestToast(toast));
        }
    }

    public static void dialogue(QuestNetwork.DialogueNode node) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueScreen screen) {
            screen.show(node);
        } else {
            minecraft.setScreen(new DialogueScreen(node));
        }
    }

    public static void dialogueClose(int session) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueScreen screen && screen.session() == session) {
            screen.closedByServer();
        }
    }

    /** On disconnect: nothing from one world may leak into the next. */
    public static void clear() {
        chapters = List.of();
        factions = List.of();
        log = List.of();
        markers = List.of();
        QUESTS.clear();
        refreshWaypoints();
    }

    /** Draws an "item:<id>" or "texture:<id>" icon in a 16x16 box. Returns false when there is none. */
    public static boolean icon(GuiGraphics graphics, String icon, int x, int y) {
        if (icon == null || icon.isEmpty()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(icon.substring(icon.indexOf(':') + 1));
        if (id == null) {
            return false;
        }
        if (icon.startsWith("texture:")) {
            graphics.blit(id, x, y, 0, 0, 16, 16, 16, 16);
            return true;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return false;
        }
        graphics.renderItem(new ItemStack(item), x, y);
        return true;
    }
}
