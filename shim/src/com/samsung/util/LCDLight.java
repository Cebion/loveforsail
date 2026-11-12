package com.samsung.util;

/** Samsung backlight API: the CFW owns the backlight, so these do nothing. */
public final class LCDLight {
    private LCDLight() {
    }

    public static boolean isSupported() {
        return false;
    }

    public static void on(int duration) {
    }

    public static void off() {
    }
}
