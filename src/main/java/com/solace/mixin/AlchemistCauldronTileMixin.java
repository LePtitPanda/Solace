package com.solace.mixin;

import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile;
import dev.obscuria.elixirum.server.alchemy.brewing.IngredientMixer;
import dev.obscuria.elixirum.common.world.NBTComponents;
import dev.obscuria.elixirum.api.codex.Alchemy;
import dev.obscuria.elixirum.api.alchemy.AlchemyIngredient;
import dev.obscuria.elixirum.common.alchemy.brewing.BrewingProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AlchemistCauldronTile.class)
public abstract class AlchemistCauldronTileMixin extends BlockEntity {

    @Shadow(remap = false)
    public AlchemistCauldronTile.AlchemistCauldronFluidHandler fluidInventory;

    private IngredientMixer solace$elixirumMixer = new IngredientMixer();

    public AlchemistCauldronTileMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void solace$writeElixirumMixer(CompoundTag tag, CallbackInfo ci) {
        NBTComponents.MIXER.write(tag, this.solace$elixirumMixer);
    }

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void solace$readElixirumMixer(CompoundTag tag, CallbackInfo ci) {
        NBTComponents.MIXER.read(tag).ifPresent(m -> this.solace$elixirumMixer = m);
    }

    @Inject(method = "isValidInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$isValidInputForElixirum(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (!Alchemy.guess().ingredients().propertiesOf(itemStack).isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tryMeltInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$tryMeltElixirumIngredient(ItemStack itemStack, CallbackInfo ci) {
        if (this.level != null && !this.level.isClientSide()) {
            if (!Alchemy.get(this.level).ingredients().propertiesOf(itemStack).isEmpty()) {
                if (this.solace$hasWater()) {
                    dev.obscuria.elixirum.api.alchemy.AlchemyRecipe recipe = this.solace$elixirumMixer.recipe();
                    if (!recipe.isComplete()) {
                        recipe = recipe.append(AlchemyIngredient.of(itemStack));
                        dev.obscuria.elixirum.api.alchemy.components.ElixirContents contents = new BrewingProcessor(Alchemy.get(this.level), recipe).brew();
                        
                        try {
                            java.lang.reflect.Field recipeField = IngredientMixer.class.getDeclaredField("recipe");
                            recipeField.setAccessible(true);
                            recipeField.set(this.solace$elixirumMixer, recipe);
                            
                            java.lang.reflect.Field contentsField = IngredientMixer.class.getDeclaredField("contents");
                            contentsField.setAccessible(true);
                            contentsField.set(this.solace$elixirumMixer, contents);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        itemStack.shrink(1);
                        this.level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                        
                        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                            dev.obscuria.fragmentum.util.color.RGB rgbColor = this.solace$elixirumMixer.getContents().color();
                            dev.obscuria.elixirum.common.world.particle.SplashParticleOptions particleOptions = new dev.obscuria.elixirum.common.world.particle.SplashParticleOptions(rgbColor, 1.0f);
                            net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(this.worldPosition);
                            serverLevel.sendParticles(particleOptions, center.x, center.y + 0.4, center.z, 32, 0.15, 0.0, 0.15, 0.0);
                        }
                        
                        this.setChanged();
                        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
                        ci.cancel();
                    }
                }
            }
        }
    }

    @Inject(method = "handleUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$handleElixirScoopOrInteraction(BlockState blockState, Level level, BlockPos pos, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(net.minecraft.world.item.Items.GLASS_BOTTLE) && this.solace$elixirumMixer.isComplete() && this.solace$hasWater()) {
            if (!level.isClientSide()) {
                ItemStack elixir = dev.obscuria.elixirum.common.registry.ElixirumItems.ELIXIR.instantiate();
                dev.obscuria.elixirum.api.ArsElixirumAPI.setElixirContents(elixir, this.solace$elixirumMixer.getContents());
                
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    dev.obscuria.elixirum.api.alchemy.AlchemyRecipe recipe = this.solace$elixirumMixer.recipe();
                    dev.obscuria.elixirum.server.alchemy.ServerAlchemyProfile profile = dev.obscuria.elixirum.server.alchemy.ServerAlchemy.get(serverLevel.getServer()).profileOf(player);
                    profile.knownRecipes().update(recipe.uuid(), dev.obscuria.elixirum.api.ArsElixirumAPI.getElixirContents(elixir));
                    profile.knownIngredients().discoverAll(recipe);
                    profile.mastery().addRecipeXp(profile, recipe.uuid(), 1);
                    profile.recipeCollection().findConfig(recipe.uuid()).ifPresent(config -> {
                        dev.obscuria.elixirum.helpers.StyleHelper.setStyleIfNotDefault(elixir, config.getStyle());
                        dev.obscuria.elixirum.helpers.StyleHelper.setChromaIfNotDefault(elixir, config.getChroma());
                    });
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        dev.obscuria.fragmentum.network.FragmentumNetworking.sendTo(serverPlayer, new dev.obscuria.elixirum.common.network.ClientboundElixirBrewedPayload(recipe));
                    }
                }
                
                this.fluidInventory.drain(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
                
                player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(itemStack, player, elixir));
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                
                this.setChanged();
                level.sendBlockUpdated(pos, blockState, blockState, 2);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide()));
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"), remap = false)
    private static void solace$tickElixirumMixer(Level level, BlockPos pos, BlockState blockState, AlchemistCauldronTile cauldronTile, CallbackInfo ci) {
        AlchemistCauldronTileMixin mixin = (AlchemistCauldronTileMixin) (Object) cauldronTile;
        if (!mixin.solace$hasWater() && !mixin.solace$elixirumMixer.isEmpty()) {
            mixin.solace$elixirumMixer.clear();
            cauldronTile.setChanged();
        }
    }

    private boolean solace$hasWater() {
        for (net.minecraftforge.fluids.FluidStack fluid : this.fluidInventory.fluids()) {
            if (fluid.getFluid() == net.minecraft.world.level.material.Fluids.WATER && fluid.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }
}
