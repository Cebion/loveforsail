package com.nokia.mid.sound;

/** Launcher access to the ringtone instrument (debug: -Dmidp.tonecycle). Not part of the Nokia API. */
public final class ShimTone {
    private static int index = -1;

    private ShimTone() {
    }

    /** Steps through NokiaTone.PROGRAMS; returns the 1-based position now selected. */
    public static synchronized int cycle(int dir) {
        int n = NokiaTone.PROGRAMS.length;
        index = index < 0 ? (dir > 0 ? 0 : n - 1) : (index + dir + n) % n;
        NokiaTone.program = NokiaTone.PROGRAMS[index];
        javax.microedition.media.ShimMidi.setProgram(0, NokiaTone.program);
        System.out.println("[media] ringtone instrument " + (index + 1) + ": " + NokiaTone.NAMES[index]
                + " (GM " + NokiaTone.program + ")");
        return index + 1;
    }
}
