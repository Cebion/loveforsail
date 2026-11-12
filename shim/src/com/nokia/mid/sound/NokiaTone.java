package com.nokia.mid.sound;

import java.io.ByteArrayOutputStream;

/**
 * Converts Nokia Smart Messaging ringing tones (Sound.FORMAT_TONE) to a one-track MIDI file played
 * on a General MIDI instrument (default 0, grand piano; -Dmidp.toneprogram=N picks another), and
 * plain beeps to a short WAV.
 *
 * Bit stream (Smart Messaging spec 3.0): command count (8), ringing-tone-programming 0x25 (7) plus
 * a filler bit, sound 0x1D (7), song type (3), title length (4) and title, pattern count (8), then
 * patterns: header id (3), pattern id (2), loop value (4), instruction count (8), instructions
 * with a 3-bit id: note (4 note, 3 duration, 2 duration specifier), scale (2), style (2),
 * tempo (5), volume (4).
 */
final class NokiaTone {
    private static final int RATE = 22050;
    private static final int[] BPM = {25, 28, 31, 35, 40, 45, 50, 56, 63, 70, 80, 90, 100, 112, 125, 140,
        160, 180, 200, 225, 250, 285, 320, 355, 400, 450, 500, 565, 635, 715, 800, 900};

    /** Candidate instruments for -Dmidp.tonecycle (GM program, name). */
    static final int[] PROGRAMS = {65, 66, 4, 5, 17, 0, 26, 56, 59, 11, 73, 71};
    static final String[] NAMES = {"Alto Sax", "Tenor Sax", "Electric Piano", "FM Electric Piano",
        "Jazz Organ", "Grand Piano", "Jazz Guitar", "Trumpet", "Muted Trumpet", "Vibraphone", "Flute",
        "Clarinet"};

    /** General MIDI program the ringtones play on. */
    static volatile int program = Integer.getInteger("midp.toneprogram", 0);

    private final byte[] bits;
    private int pos;

    private NokiaTone(byte[] bits) {
        this.bits = bits;
    }

    private int read(int n) {
        int v = 0;
        for (int i = 0; i < n; i++) {
            int idx = pos >> 3;
            int b = idx < bits.length ? bits[idx] & 0xFF : 0;
            v = (v << 1) | ((b >> (7 - (pos & 7))) & 1);
            pos++;
        }
        return v;
    }

    private boolean more(int n) {
        return pos + n <= bits.length * 8;
    }

    /** Standard MIDI file bytes for a ringing tone, or null when the data isn't one. */
    static byte[] toneToMidi(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        NokiaTone t = new NokiaTone(data);
        Midi midi = new Midi(program);
        try {
            t.render(midi);
        } catch (RuntimeException e) {
            System.out.println("[media] bad Nokia tone data: " + e);
        }
        return midi.notes == 0 ? null : midi.smf();
    }

    /** WAV bytes for a plain beep (Sound(int freq, long duration)). */
    static byte[] beepToWav(int freq, long durationMs) {
        Pcm pcm = new Pcm();
        pcm.tone(freq, (int) Math.max(1, durationMs), 0.25);
        return pcm.wav();
    }

    /** WAV bytes of silence, for tone data that can't be decoded. */
    static byte[] silenceToWav(int ms) {
        Pcm pcm = new Pcm();
        pcm.silence(ms);
        return pcm.wav();
    }

    /** Receives the decoded melody. note: 1..12 = C..B, 0 = pause; octave: 0 = scale-1 (A = 440 Hz). */
    private interface Out {
        void note(int note, int octave, int ms, int onMs, int volume);
    }

    private void render(Out pcm) {
        int commands = read(8);
        int[] patternStart = new int[4];
        int[] patternBits = new int[4];
        for (int c = 0; c < commands && more(7); c++) {
            int cmd = read(7);
            if (cmd == 0x25) {          // ringing-tone-programming, followed by a filler bit
                read(1);
            } else if (cmd == 0x1D) {   // sound
                int songType = read(3);
                if (songType == 1) {    // basic song: has a title
                    int titleLen = read(4);
                    read(8 * titleLen);
                }
                int patterns = read(8);
                State st = new State();
                for (int p = 0; p < patterns && more(17); p++) {
                    read(3);
                    int id = read(2);
                    int loop = read(4);
                    int count = read(8);
                    int start = pos;
                    if (count == 0) {   // repeat an earlier pattern with this id
                        int save = pos;
                        playPattern(pcm, st, patternStart[id], patternBits[id], loop);
                        pos = save;
                        continue;
                    }
                    patternStart[id] = start;
                    patternBits[id] = count;
                    playPattern(pcm, st, start, count, loop);
                }
                return;
            } else {
                return;
            }
        }
    }

    private void playPattern(Out pcm, State st, int start, int count, int loop) {
        int times = loop == 15 ? 2 : 1 + loop;   // 15 = "infinite"; the game loops the sound itself
        int end = start;
        for (int r = 0; r < times; r++) {
            pos = start;
            for (int i = 0; i < count && more(3); i++) {
                int id = read(3);
                switch (id) {
                    case 1: {
                        int note = read(4);
                        int dur = read(3);
                        int spec = read(2);
                        double beats = 4.0 / (1 << Math.min(dur, 5));
                        if (spec == 1) {
                            beats *= 1.5;
                        } else if (spec == 2) {
                            beats *= 1.75;
                        } else if (spec == 3) {
                            beats *= 2.0 / 3.0;
                        }
                        int ms = (int) (beats * 60000.0 / st.bpm);
                        int on = st.style == 1 ? ms : st.style == 2 ? ms / 2 : ms * 15 / 16;
                        pcm.note(note > 12 ? 0 : note, st.scale, ms, on, st.volume);
                        break;
                    }
                    case 2:
                        st.scale = read(2);
                        break;
                    case 3:
                        st.style = read(2);
                        break;
                    case 4:
                        st.bpm = BPM[read(5)];
                        break;
                    case 5:
                        st.volume = read(4);
                        break;
                    default:
                        return;
                }
            }
            end = pos;
        }
        pos = end;
    }

    private static final class State {
        int bpm = 63;
        int scale = 1;      // scale-2 (A = 880 Hz) is the default
        int style = 0;
        int volume = 7;
    }

    /** Single-track SMF, 480 ticks per quarter at 125 BPM: one tick per millisecond. */
    private static final class Midi implements Out {
        private final ByteArrayOutputStream track = new ByteArrayOutputStream();
        private final int program;
        private long pendingMs;
        int notes;

        Midi(int program) {
            this.program = Math.max(0, Math.min(127, program));
            event(0, 0xC0, this.program, -1);
        }

        public void note(int note, int octave, int ms, int onMs, int volume) {
            if (note == 0) {
                pendingMs += ms;
                return;
            }
            int key = Math.max(0, Math.min(127, 69 + (note - 10) + 12 * octave));
            int vel = Math.max(1, Math.min(127, 40 + volume * 6));
            event(pendingMs, 0x90, key, vel);
            event(onMs, 0x80, key, 0);
            pendingMs = ms - onMs;
            notes++;
        }

        private void event(long delta, int status, int a, int b) {
            writeVar(delta);
            track.write(status);
            track.write(a);
            if (b >= 0) {
                track.write(b);
            }
        }

        private void writeVar(long v) {
            long buf = v & 0x7F;
            while ((v >>= 7) > 0) {
                buf <<= 8;
                buf |= ((v & 0x7F) | 0x80);
            }
            while (true) {
                track.write((int) (buf & 0xFF));
                if ((buf & 0x80) != 0) {
                    buf >>= 8;
                } else {
                    break;
                }
            }
        }

        byte[] smf() {
            ByteArrayOutputStream t = new ByteArrayOutputStream();
            // tempo 480000 us per quarter (125 BPM) with 480 ticks per quarter = 1 ms per tick
            t.write(0);
            t.write(0xFF);
            t.write(0x51);
            t.write(3);
            t.write(0x07);
            t.write(0x53);
            t.write(0x00);
            byte[] body = track.toByteArray();
            t.write(body, 0, body.length);
            writeVarTo(t, pendingMs);
            t.write(0xFF);
            t.write(0x2F);
            t.write(0);
            byte[] tr = t.toByteArray();
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            writeStr(f, "MThd");
            writeBE(f, 6, 4);
            writeBE(f, 0, 2);
            writeBE(f, 1, 2);
            writeBE(f, 480, 2);
            writeStr(f, "MTrk");
            writeBE(f, tr.length, 4);
            f.write(tr, 0, tr.length);
            return f.toByteArray();
        }

        private static void writeVarTo(ByteArrayOutputStream o, long v) {
            Midi m = new Midi(0);
            m.track.reset();
            m.writeVar(v);
            byte[] b = m.track.toByteArray();
            o.write(b, 0, b.length);
        }

        private static void writeStr(ByteArrayOutputStream w, String s) {
            for (int i = 0; i < s.length(); i++) {
                w.write(s.charAt(i));
            }
        }

        private static void writeBE(ByteArrayOutputStream w, int v, int n) {
            for (int i = n - 1; i >= 0; i--) {
                w.write((v >> (8 * i)) & 0xFF);
            }
        }
    }

    private static final class Pcm {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private double phase;

        int size() {
            return out.size();
        }

        void silence(int ms) {
            int n = (int) ((long) RATE * Math.max(0, ms) / 1000);
            for (int i = 0; i < n; i++) {
                out.write(0);
                out.write(0);
            }
        }

        void tone(double freq, int ms, double amp) {
            int n = (int) ((long) RATE * Math.max(0, ms) / 1000);
            int fade = Math.min(n / 4, RATE / 500);   // 2 ms ramps to avoid clicks
            double step = freq / RATE;
            for (int i = 0; i < n; i++) {
                double env = 1.0;
                if (i < fade) {
                    env = (double) i / fade;
                } else if (i >= n - fade) {
                    env = (double) (n - 1 - i) / fade;
                }
                phase += step;
                if (phase >= 1.0) {
                    phase -= Math.floor(phase);
                }
                int s = (int) ((phase < 0.5 ? 1 : -1) * amp * env * 32767);
                out.write(s & 0xFF);
                out.write((s >> 8) & 0xFF);
            }
        }

        byte[] wav() {
            byte[] pcm = out.toByteArray();
            ByteArrayOutputStream w = new ByteArrayOutputStream(44 + pcm.length);
            writeStr(w, "RIFF");
            writeInt(w, 36 + pcm.length);
            writeStr(w, "WAVEfmt ");
            writeInt(w, 16);
            writeShort(w, 1);
            writeShort(w, 1);
            writeInt(w, RATE);
            writeInt(w, RATE * 2);
            writeShort(w, 2);
            writeShort(w, 16);
            writeStr(w, "data");
            writeInt(w, pcm.length);
            w.write(pcm, 0, pcm.length);
            return w.toByteArray();
        }

        private static void writeStr(ByteArrayOutputStream w, String s) {
            for (int i = 0; i < s.length(); i++) {
                w.write(s.charAt(i));
            }
        }

        private static void writeInt(ByteArrayOutputStream w, int v) {
            w.write(v & 0xFF);
            w.write((v >> 8) & 0xFF);
            w.write((v >> 16) & 0xFF);
            w.write((v >> 24) & 0xFF);
        }

        private static void writeShort(ByteArrayOutputStream w, int v) {
            w.write(v & 0xFF);
            w.write((v >> 8) & 0xFF);
        }
    }
}
