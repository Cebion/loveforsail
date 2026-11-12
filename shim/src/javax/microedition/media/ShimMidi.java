package javax.microedition.media;

/** Launcher/shim access to the shared MIDI synthesizer. Not part of MMAPI. */
public final class ShimMidi {
    private ShimMidi() {
    }

    /** Sends a program change straight to the synthesizer (affects a tune that is playing). */
    public static void setProgram(int channel, int program) {
        MidiPlayer.setProgram(channel, program);
    }
}
