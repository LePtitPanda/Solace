package com.solace.mixin;

import com.faboslav.friendsandfoes.common.entity.TuffGolemEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TuffGolemEntity.class, remap = false)
public abstract class TuffGolemNaturalSpawnMixin extends AbstractGolem {
    @Unique
    private static final float SOLACE_HOLD_ITEM_CHANCE = 0.65F;
    @Unique
    private static final WeightedItem[] SOLACE_HELD_ITEMS = new WeightedItem[] {
            new WeightedItem(Items.COBBLESTONE, 8),
            new WeightedItem(Items.TUFF, 6),
            new WeightedItem(Items.GRAVEL, 5),
            new WeightedItem(Items.RAW_IRON, 4),
            new WeightedItem(Items.RAW_COPPER, 4),
            new WeightedItem(Items.BONE, 3),
            new WeightedItem(Items.ROTTEN_FLESH, 3),
            new WeightedItem(Items.LAPIS_LAZULI, 2),
            new WeightedItem(Items.DIAMOND, 1)
    };

    protected TuffGolemNaturalSpawnMixin(EntityType<? extends AbstractGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow(remap = false)
    public abstract void setColor(TuffGolemEntity.Color color);

    @Shadow(remap = false)
    public abstract void startStandingWithItem();

    @Inject(method = "m_6518_", at = @At("RETURN"), remap = false)
    private void solace$setupNaturalSpawnAppearance(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnReason,
            SpawnGroupData spawnData,
            CompoundTag entityTag,
            CallbackInfoReturnable<SpawnGroupData> cir
    ) {
        if (spawnReason == MobSpawnType.SPAWN_EGG || spawnReason == MobSpawnType.COMMAND) {
            return;
        }

        RandomSource random = this.getRandom();
        TuffGolemEntity.Color[] colors = TuffGolemEntity.Color.values();
        this.setColor(colors[random.nextInt(colors.length)]);

        if (random.nextFloat() >= SOLACE_HOLD_ITEM_CHANCE) {
            return;
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(solace$pickHeldItem(random)));
        this.startStandingWithItem();
    }

    @Unique
    private static Item solace$pickHeldItem(RandomSource random) {
        int totalWeight = 0;
        for (WeightedItem entry : SOLACE_HELD_ITEMS) {
            totalWeight += entry.weight();
        }

        int selected = random.nextInt(totalWeight);
        for (WeightedItem entry : SOLACE_HELD_ITEMS) {
            selected -= entry.weight();
            if (selected < 0) {
                return entry.item();
            }
        }

        return Items.COBBLESTONE;
    }

    @Unique
    private record WeightedItem(Item item, int weight) {
    }
}
