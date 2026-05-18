package com.solace.client;

public final class SolaceClientState {
    private static boolean lastCreatedWorldWasHardcore;

    private SolaceClientState() {
    }

    public static void setLastCreatedWorldWasHardcore(boolean hardcore) {
        lastCreatedWorldWasHardcore = hardcore;
    }

    public static boolean wasLastCreatedWorldHardcore() {
        return lastCreatedWorldWasHardcore;
    }
}
