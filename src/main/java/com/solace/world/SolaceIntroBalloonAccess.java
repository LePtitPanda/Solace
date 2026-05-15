package com.solace.world;

public interface SolaceIntroBalloonAccess {
    void solace$setIntroTarget(double x, double y, double z);

    boolean solace$isIntroBalloon();

    boolean solace$hasLanded();
}
