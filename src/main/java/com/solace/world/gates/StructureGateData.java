package com.solace.world.gates;

import com.solace.world.gates.StructureGateConfig.Entry;
import com.solace.world.gates.StructureGateConfig.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What happened to each gate in this world: data/solace_structure_gates.dat in the overworld.
 * Worldgen threads read and claim concurrently, so every access is synchronized.
 */
public final class StructureGateData extends SavedData {
    /** Where a once copy generated. Plain ids, so this class needs no bootstrapped registries. */
    public record SavedPos(ResourceLocation dimension, BlockPos pos) {
    }

    private static final class Gate {
        boolean open;
        Mode mode;
        final List<SavedPos> positions = new ArrayList<>();

        Gate(boolean open, Mode mode) {
            this.open = open;
            this.mode = mode;
        }
    }

    /** Only structures something happened to have a record; the rest follow the config defaults. */
    private final Map<ResourceLocation, Gate> gates = new HashMap<>();

    static StructureGateData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(StructureGateData::load, StructureGateData::new,
                "solace_structure_gates");
    }

    private Gate gate(ResourceLocation id, Entry entry) {
        return gates.computeIfAbsent(id, key -> new Gate(entry.initiallyOpen(), entry.mode()));
    }

    synchronized boolean isOpen(ResourceLocation id, Entry entry) {
        Gate gate = gates.get(id);
        return gate == null ? entry.initiallyOpen() : gate.open;
    }

    synchronized Mode mode(ResourceLocation id, Entry entry) {
        Gate gate = gates.get(id);
        return gate == null ? entry.mode() : gate.mode;
    }

    /** The atomic once-claim: true if this start may be placed. A once gate closes behind it. */
    synchronized boolean claim(ResourceLocation id, Entry entry, SavedPos pos) {
        Gate gate = gate(id, entry);
        if (!gate.open) {
            return false;
        }
        if (gate.mode == Mode.ONCE) {
            gate.open = false;
            gate.positions.add(pos);
            setDirty();
        }
        return true;
    }

    /** False when nothing changed: already open in that mode, or a once copy already exists. */
    synchronized boolean open(ResourceLocation id, Entry entry, Mode mode) {
        Gate gate = gate(id, entry);
        if (mode == Mode.ONCE && !gate.positions.isEmpty() || gate.open && gate.mode == mode) {
            return false;
        }
        gate.open = true;
        gate.mode = mode;
        setDirty();
        return true;
    }

    synchronized boolean close(ResourceLocation id, Entry entry) {
        Gate gate = gate(id, entry);
        if (!gate.open) {
            return false;
        }
        gate.open = false;
        setDirty();
        return true;
    }

    synchronized boolean reset(ResourceLocation id) {
        if (gates.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    synchronized List<SavedPos> positions(ResourceLocation id) {
        Gate gate = gates.get(id);
        return gate == null ? List.of() : List.copyOf(gate.positions);
    }

    static StructureGateData load(CompoundTag tag) {
        StructureGateData data = new StructureGateData();
        CompoundTag all = tag.getCompound("gates");
        for (String key : all.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            CompoundTag gateTag = all.getCompound(key);
            Mode mode = Mode.parse(gateTag.getString("mode"));
            if (id == null || mode == null) {
                continue;
            }
            Gate gate = new Gate(gateTag.getBoolean("open"), mode);
            for (Tag element : gateTag.getList("positions", Tag.TAG_COMPOUND)) {
                CompoundTag posTag = (CompoundTag) element;
                ResourceLocation dimension = ResourceLocation.tryParse(posTag.getString("dimension"));
                if (dimension != null) {
                    gate.positions.add(new SavedPos(dimension,
                            new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"))));
                }
            }
            data.gates.put(id, gate);
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        gates.forEach((id, gate) -> {
            CompoundTag gateTag = new CompoundTag();
            gateTag.putBoolean("open", gate.open);
            gateTag.putString("mode", gate.mode.id());
            ListTag positions = new ListTag();
            for (SavedPos pos : gate.positions) {
                CompoundTag posTag = new CompoundTag();
                posTag.putString("dimension", pos.dimension().toString());
                posTag.putInt("x", pos.pos().getX());
                posTag.putInt("y", pos.pos().getY());
                posTag.putInt("z", pos.pos().getZ());
                positions.add(posTag);
            }
            gateTag.put("positions", positions);
            all.put(id.toString(), gateTag);
        });
        tag.put("gates", all);
        return tag;
    }
}
