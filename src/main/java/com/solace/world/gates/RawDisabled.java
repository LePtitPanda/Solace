package com.solace.world.gates;

/** Added to Structurify's StructureData: its own stored is_disabled, ignoring the gate override. */
public interface RawDisabled {
    boolean solace$rawDisabled();
}
