package javax.microedition.media;

import java.io.ByteArrayInputStream;
import java.io.File;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;

/**
 * MIDI player backed by one shared Java Sound sequencer + software synthesizer (Gervill).
 * Only one MIDI plays at a time, like on most handsets; starting a player takes the sequencer
 * from whichever player had it. Falls back to silent timing if Java Sound is unavailable.
 */
class MidiPlayer extends BasePlayer {
    private static Sequencer sequencer;
    private static Synthesizer synth;
    private static Receiver synthReceiver;
    private static boolean engineFailed;
    private static MidiPlayer owner;
    /** Headroom: a full arrangement at full volume clips. */
    private static final float MASTER = 0.7f;

    private final byte[] data;
    private Sequence sequence;
    private SilentPlayer fallback;
    private long pausedTick;

    MidiPlayer(byte[] data, String type) {
        super(type);
        this.data = data;
    }

    private static synchronized boolean ensureEngine() {
        if (sequencer != null) {
            return true;
        }
        if (engineFailed) {
            return false;
        }
        try {
            long t0 = System.currentTimeMillis();
            synth = MidiSystem.getSynthesizer();
            String sf = System.getProperty("midp.soundfont");
            boolean haveSf = sf != null && new File(sf).isFile();
            boolean tuned = haveSf && openTuned(synth);
            if (!tuned) {
                synth.open();
            }
            if (haveSf) {
                Soundbank bank = MidiSystem.getSoundbank(new File(sf));
                if (synth.isSoundbankSupported(bank)) {
                    if (!tuned) {
                        synth.unloadAllInstruments(synth.getDefaultSoundbank());
                    }
                    synth.loadAllInstruments(bank);
                    System.out.println("[media] loaded soundfont " + sf + (tuned ? " (no reverb/chorus, 32 voices)" : "")
                            + " in " + (System.currentTimeMillis() - t0) + " ms");
                }
            }
            synthReceiver = synth.getReceiver();
            sequencer = MidiSystem.getSequencer(false);
            sequencer.getTransmitter().setReceiver(synthReceiver);
            sequencer.open();
            sequencer.addMetaEventListener((MetaMessage m) -> {
                if (m.getType() == 47) {
                    MidiPlayer p;
                    synchronized (MidiPlayer.class) {
                        p = owner;
                        owner = null;
                    }
                    if (p != null) {
                        p.pausedTick = 0;
                        p.endOfMedia();
                    }
                }
            });
            System.out.println("[media] MIDI engine ready: " + synth.getDeviceInfo().getName());
            if (Boolean.getBoolean("midp.mididebug")) {
                Thread t = new Thread(() -> {
                    while (true) {
                        StringBuilder sb = new StringBuilder("[mididebug]");
                        javax.sound.midi.MidiChannel[] chs = synth.getChannels();
                        for (int c = 0; c < chs.length; c++) {
                            if (chs[c] != null) {
                                sb.append(' ').append(c).append(':').append(chs[c].getProgram());
                            }
                        }
                        sb.append(" running=").append(sequencer.isRunning());
                        System.out.println(sb);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }, "mididebug");
                t.setDaemon(true);
                t.start();
            }
            return true;
        } catch (Throwable t) {
            System.out.println("[media] MIDI unavailable, music disabled: " + t);
            engineFailed = true;
            sequencer = null;
            return false;
        }
    }

    /**
     * Gervill's own settings (com.sun.media.sound.AudioSynthesizer, not public API): skip its
     * generated fallback instruments when a soundfont replaces them (2-3 s on an RK3326), and drop
     * reverb and chorus, which cost CPU (crackling) and blur busy arrangements. Larger buffer, 32 voices.
     */
    private static boolean openTuned(Synthesizer s) {
        try {
            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("load default soundbank", Boolean.FALSE);
            info.put("reverb", Boolean.FALSE);
            info.put("chorus", Boolean.FALSE);
            info.put("max polyphony", 32);
            info.put("latency", 200000L);
            java.lang.reflect.Method open = null;
            for (Class<?> c = s.getClass(); c != null && open == null; c = c.getSuperclass()) {
                try {
                    open = c.getDeclaredMethod("open", javax.sound.sampled.SourceDataLine.class, java.util.Map.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (open == null) {
                return false;
            }
            open.setAccessible(true);
            open.invoke(s, null, info);
            return true;
        } catch (Throwable t) {
            System.out.println("[media] tuned synthesizer setup unavailable (" + t + "), using defaults");
            return false;
        }
    }

    /** Opens the synthesizer and loads the soundfont ahead of the first song (called at startup). */
    static void preload() {
        ensureEngine();
    }

    protected void doPrefetch() throws MediaException {
        try {
            sequence = MidiSystem.getSequence(new ByteArrayInputStream(data));
        } catch (Exception e) {
            throw new MediaException("bad MIDI data: " + e);
        }
        if (!ensureEngine()) {
            fallback = new SilentPlayer(contentType, sequence.getMicrosecondLength() / 1000);
            fallback.addPlayerListener((pl, ev, d) -> {
                if (PlayerListener.END_OF_MEDIA.equals(ev)) {
                    endOfMedia();
                }
            });
        }
    }

    protected void doStart() throws MediaException {
        if (fallback != null) {
            fallback.start();
            return;
        }
        synchronized (MidiPlayer.class) {
            if (owner != null && owner != this) {
                owner.stolen();
            }
            owner = this;
            try {
                sequencer.stop();
                sequencer.setSequence(sequence);
                sequencer.setLoopCount(loopCount < 0 ? Sequencer.LOOP_CONTINUOUSLY : loopCount - 1);
                sequencer.setTickPosition(pausedTick);
                applyVolume();
                sequencer.start();
            } catch (Exception e) {
                owner = null;
                throw new MediaException("MIDI start failed: " + e);
            }
        }
    }

    /** Another player took the sequencer: drop to PREFETCHED without an event, like a stop(). */
    private void stolen() {
        pausedTick = 0;
        state = PREFETCHED;
    }

    protected void doStop() {
        if (fallback != null) {
            try {
                fallback.stop();
            } catch (MediaException ignored) {
            }
            return;
        }
        synchronized (MidiPlayer.class) {
            if (owner == this) {
                pausedTick = sequencer.getTickPosition();
                sequencer.stop();
                allNotesOff();
                owner = null;
            }
        }
    }

    protected void doClose() {
        doStop();
        sequence = null;
    }

    static void setProgram(int channel, int program) {
        synchronized (MidiPlayer.class) {
            if (synthReceiver == null) {
                return;
            }
            try {
                javax.sound.midi.ShortMessage m = new javax.sound.midi.ShortMessage();
                m.setMessage(javax.sound.midi.ShortMessage.PROGRAM_CHANGE, channel, program, 0);
                synthReceiver.send(m, -1);
            } catch (Exception ignored) {
            }
        }
    }

    private static void allNotesOff() {
        try {
            for (javax.sound.midi.MidiChannel ch : synth.getChannels()) {
                if (ch != null) {
                    ch.allNotesOff();
                    ch.allSoundOff();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    protected void applyVolume() {
        synchronized (MidiPlayer.class) {
            if (fallback != null || synthReceiver == null || owner != this) {
                return;
            }
            int v = Math.round(gain() * MASTER * 16383);
            try {
                // Universal real-time SysEx: master volume (Gervill honours it).
                byte[] msg = {(byte) 0xF0, 0x7F, 0x7F, 0x04, 0x01, (byte) (v & 0x7F), (byte) ((v >> 7) & 0x7F), (byte) 0xF7};
                SysexMessage sx = new SysexMessage();
                sx.setMessage(msg, msg.length);
                synthReceiver.send(sx, -1);
            } catch (Exception ignored) {
            }
        }
    }

    public long setMediaTime(long now) throws MediaException {
        if (fallback != null) {
            return fallback.setMediaTime(now);
        }
        synchronized (MidiPlayer.class) {
            if (owner == this) {
                sequencer.setMicrosecondPosition(Math.max(0, now));
                return sequencer.getMicrosecondPosition();
            }
        }
        if (now <= 0 || sequence == null) {
            pausedTick = 0;
            return 0;
        }
        long len = sequence.getMicrosecondLength();
        pausedTick = len > 0 ? Math.min(now, len) * sequence.getTickLength() / len : 0;
        return now;
    }

    public long getMediaTime() {
        if (fallback != null) {
            return fallback.getMediaTime();
        }
        synchronized (MidiPlayer.class) {
            if (owner == this) {
                return sequencer.getMicrosecondPosition();
            }
        }
        if (sequence == null || sequence.getTickLength() == 0) {
            return 0;
        }
        return pausedTick * sequence.getMicrosecondLength() / sequence.getTickLength();
    }

    public long getDuration() {
        return sequence == null ? TIME_UNKNOWN : sequence.getMicrosecondLength();
    }
}
