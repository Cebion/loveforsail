package com.samsung.util;

/** Samsung vibration API: no rumble on the handheld, so these do nothing. */
public final class Vibration {
    private Vibration() {
    }

    public static boolean isSupported() {
        return false;
    }

    public static void start(int duration, int strength) {
    }

    public static void stop() {
    }
}
