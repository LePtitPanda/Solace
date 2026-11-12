package com.solace.quests.content;

/** One validation finding, located by content file and JSON path. */
public record Problem(String kind, String id, String path, boolean error, String message) {
    @Override
    public String toString() {
        return (error ? "ERROR " : "WARN  ") + id + " (" + kind + ") " + path + ": " + message;
    }
}
