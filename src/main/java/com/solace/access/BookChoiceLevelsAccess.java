package com.solace.access;

import net.backupcup.mcde.util.SlotPosition;

public interface BookChoiceLevelsAccess {
    int solace$getChoiceLevel(SlotPosition position);

    void solace$setChoiceLevel(SlotPosition position, int level);
}
