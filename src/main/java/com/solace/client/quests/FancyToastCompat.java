package com.solace.client.quests;

import com.solace.quests.network.QuestNetwork;
import net.bivrik.fancytoasts.FancyToasts;
import net.bivrik.fancytoasts.platform.utility.QuestDisplay;
import net.bivrik.fancytoasts.platform.utility.QuestType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Shows a quest toast through the Fancy Toasts mod, styled like its own quest toasts. Only loaded when it is installed. */
final class FancyToastCompat {
    private FancyToastCompat() {
    }

    static void show(QuestNetwork.Toast toast) {
        // Fancy Toasts draws item icons only; texture icons fall back to a book.
        Item item = toast.icon() != null && toast.icon().startsWith("item:")
                ? ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(toast.icon().substring(5))) : null;
        ItemStack icon = new ItemStack(item == null || item == Items.AIR ? Items.WRITABLE_BOOK : item);
        QuestType type = QuestType.QUEST;
        FancyToasts fancy = FancyToasts.getInstance();
        fancy.getToastManager().add(new QuestDisplay(List.of(icon), toast.subtitle(), Component.empty(), toast.title(),
                type.getTitleColor(), type.getDescriptionColor(), type.getConventionalType(), type),
                fancy.getConfigManager().getToastConfigData().getSoundIdByQuestType(type), null);
    }
}
