package com.solace.mixin;

import com.solace.ElixirumCauldronHolder;
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
public abstract class AlchemistCauldronTileMixin extends BlockEntity implements ElixirumCauldronHolder {

    @Shadow(remap = false)
    public AlchemistCauldronTile.AlchemistCauldronFluidHandler fluidInventory;

    @Shadow(remap = false)
    public abstract boolean isBrewable(ItemStack stack);

    private IngredientMixer solace$elixirumMixer = new IngredientMixer();

    public AlchemistCauldronTileMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Object solace$getElixirumMixer() {
        return this.solace$elixirumMixer;
    }

    @Override
    public boolean solace$hasElixirumRecipe() {
        return this.solace$elixirumMixer != null && !this.solace$elixirumMixer.isEmpty();
    }

    @Override
    public int solace$getElixirumColor() {
        if (this.solace$elixirumMixer != null) {
            dev.obscuria.fragmentum.api.common.color.RGB rgb = this.solace$elixirumMixer.color();
            if (rgb != null) {
                int r = (int) (rgb.getRed() * 255.0f);
                int g = (int) (rgb.getGreen() * 255.0f);
                int b = (int) (rgb.getBlue() * 255.0f);
                return (255 << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return 0;
    }

    @Inject(method = "m_5995_", at = @At("RETURN"), remap = false)
    private void solace$getUpdateTag(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag != null) {
            NBTComponents.MIXER.write(tag, this.solace$elixirumMixer);
        }
    }

    @Inject(method = "handleUpdateTag", at = @At("TAIL"), remap = false)
    private void solace$handleUpdateTag(CompoundTag tag, CallbackInfo ci) {
        NBTComponents.MIXER.read(tag).ifPresent(m -> this.solace$elixirumMixer = m);
    }

    @Inject(method = "m_183515_", at = @At("TAIL"), remap = false)
    private void solace$writeElixirumMixer(CompoundTag tag, CallbackInfo ci) {
        NBTComponents.MIXER.write(tag, this.solace$elixirumMixer);
    }

    @Inject(method = "m_142466_", at = @At("TAIL"), remap = false)
    private void solace$readElixirumMixer(CompoundTag tag, CallbackInfo ci) {
        NBTComponents.MIXER.read(tag).ifPresent(m -> this.solace$elixirumMixer = m);
    }

    private boolean solace$isVanillaPotionIngredient(ItemStack stack) {
        net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!itemId.getNamespace().equals("minecraft")) {
            return false;
        }
        String path = itemId.getPath();
        return path.equals("nether_wart") 
            || path.equals("redstone") 
            || path.equals("glowstone_dust") 
            || path.equals("gunpowder") 
            || path.equals("sugar") 
            || path.equals("spider_eye") 
            || path.equals("fermented_spider_eye") 
            || path.equals("ghast_tear") 
            || path.equals("rabbit_foot") 
            || path.equals("blaze_powder") 
            || path.equals("glistering_melon_slice") 
            || path.equals("golden_carrot") 
            || path.equals("pufferfish") 
            || path.equals("magma_cream") 
            || path.equals("phantom_membrane") 
            || path.equals("turtle_helmet")
            || path.equals("turtle_scute");
    }

    private boolean solace$isExistingRecipeInput(ItemStack stack) {
        if (this.level == null) return false;
        
        // 1. Is it a scroll?
        if (stack.is(io.redspace.ironsspellbooks.registries.ItemRegistry.SCROLL.get())) {
            return true;
        }
        
        // 2. If the cauldron contains water, check for water-based Iron's Spells/vanilla recipes
        if (this.solace$hasWater()) {
            net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            String namespace = itemId.getNamespace();
            String path = itemId.getPath();
            
            if (namespace.equals("irons_spellbooks")) {
                if (path.equals("icy_fang") || path.equals("shriving_stone")) {
                    return true;
                }
            }
            
            if (namespace.equals("minecraft")) {
                if (path.equals("ender_pearl") || path.equals("echo_shard") || path.equals("oak_log")) {
                    return true;
                }
            }
            
            // Check for vanilla potion ingredients
            if (this.solace$isVanillaPotionIngredient(stack)) {
                return true;
            }
            
            // For water, anything else is NOT an existing recipe input
            return false;
        }
        
        // 3. If the cauldron does NOT contain water, fallback to checking isBrewable
        return this.isBrewable(stack);
    }

    @Inject(method = "isValidInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$isValidInputForElixirum(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        System.out.println("[SolaceDebug] isValidInput: " + itemStack.toString());
        boolean isExisting = this.solace$isExistingRecipeInput(itemStack);
        System.out.println("[SolaceDebug] isExistingRecipeInput: " + isExisting);
        if (!isExisting) {
            boolean isElixirumIngredient = !Alchemy.guess().ingredients().propertiesOf(itemStack).isEmpty();
            System.out.println("[SolaceDebug] isElixirumIngredient (guess): " + isElixirumIngredient);
            if (isElixirumIngredient) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "tryMeltInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$tryMeltElixirumIngredient(ItemStack itemStack, CallbackInfo ci) {
        if (this.level != null && !this.level.isClientSide()) {
            System.out.println("[SolaceDebug] tryMeltInput called for: " + itemStack.toString());
            boolean isExisting = this.solace$isExistingRecipeInput(itemStack);
            System.out.println("[SolaceDebug] tryMeltInput - isExistingRecipeInput: " + isExisting);
            if (!isExisting) {
                boolean isElixirumIngredient = !Alchemy.get(this.level).ingredients().propertiesOf(itemStack).isEmpty();
                System.out.println("[SolaceDebug] tryMeltInput - isElixirumIngredient (level): " + isElixirumIngredient);
                if (isElixirumIngredient) {
                    boolean hasWater = this.solace$hasWater();
                    System.out.println("[SolaceDebug] tryMeltInput - hasWater: " + hasWater);
                    if (hasWater) {
                        dev.obscuria.elixirum.api.alchemy.AlchemyRecipe recipe = this.solace$elixirumMixer.recipe();
                        System.out.println("[SolaceDebug] tryMeltInput - current recipe complete: " + recipe.isComplete());
                        if (!recipe.isComplete()) {
                            recipe = recipe.append(AlchemyIngredient.of(itemStack));
                            dev.obscuria.elixirum.api.alchemy.components.ElixirContents contents = new BrewingProcessor(Alchemy.get(this.level), recipe).brew();
                            System.out.println("[SolaceDebug] tryMeltInput - brewed contents color: " + contents.color());
                            
                            try {
                                java.lang.reflect.Field recipeField = IngredientMixer.class.getDeclaredField("recipe");
                                recipeField.setAccessible(true);
                                recipeField.set(this.solace$elixirumMixer, recipe);
                                
                                java.lang.reflect.Field contentsField = IngredientMixer.class.getDeclaredField("contents");
                                contentsField.setAccessible(true);
                                contentsField.set(this.solace$elixirumMixer, contents);
                            } catch (Exception e) {
                                System.out.println("[SolaceDebug] tryMeltInput - Reflection failed!");
                                e.printStackTrace();
                            }
                            
                            itemStack.shrink(1);
                            this.level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                            
                            if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                                dev.obscuria.fragmentum.api.common.color.RGB rgbColor = this.solace$elixirumMixer.getContents().color();
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
    }

    @Inject(method = "handleUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$handleElixirScoopOrInteraction(BlockState blockState, Level level, BlockPos pos, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(net.minecraft.world.item.Items.GLASS_BOTTLE) && this.solace$elixirumMixer.isComplete() && this.solace$hasWater()) {
            if (!level.isClientSide()) {
                ItemStack elixir = dev.obscuria.elixirum.common.registry.ElixirumItems.ELIXIR.get().getDefaultInstance();
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
                        dev.obscuria.fragmentum.api.network.FragmentumNetworking.INSTANCE.sendTo(serverPlayer, new dev.obscuria.elixirum.common.network.ClientboundElixirBrewedPayload(recipe));
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
