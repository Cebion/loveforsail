package javax.microedition.media;

/** Launcher/shim access to the shared MIDI synthesizer. Not part of MMAPI. */
public final class ShimMidi {
    private ShimMidi() {
    }

    /** Opens the synthesizer and loads the soundfont now, so the first song doesn't wait for it. */
    public static void preload() {
        MidiPlayer.preload();
    }

    /** Sends a program change straight to the synthesizer (affects a tune that is playing). */
    public static void setProgram(int channel, int program) {
        MidiPlayer.setProgram(channel, program);
    }
}
