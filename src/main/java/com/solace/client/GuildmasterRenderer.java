package com.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solace.Solace;
import com.solace.entity.Guildmaster;
import com.solace.registry.SolaceEntities;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla villager model with the Guildmaster's own 64×64 skin. */
public class GuildmasterRenderer extends MobRenderer<Guildmaster, VillagerModel<Guildmaster>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Solace.MOD_ID, "textures/entity/guildmaster.png");

    public GuildmasterRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(Guildmaster entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Guildmaster entity, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SolaceEntities.GUILDMASTER.get(), GuildmasterRenderer::new);
        }

        private Setup() {
        }
    }
}
