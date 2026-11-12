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

        void tempo(int bpm);
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
                pcm.tempo(st.bpm);
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
                        pcm.tempo(st.bpm);
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

    /**
     * Writes the melody plus, unless -Dmidp.tonearrange=false, a simple accompaniment derived from
     * it: every two beats the triad of the song's key (I ii iii IV V vi) that best covers the melody
     * notes sets a bass note (root, or the fifth when the chord doesn't change), over a soft lounge groove (hi-hat eighths, kick on
     * 1 and 3, side stick on 2 and 4). Ringtones carry only one voice, so this is all invention.
     * One-track SMF (format 0), 480 ticks per quarter at 125 BPM: one tick per millisecond.
     */
    private static final class Midi implements Out {
        private static final int BASS_PROGRAM = 32;   // acoustic bass
        private final int program;
        private final boolean arrange = !"false".equals(System.getProperty("midp.tonearrange"));
        /** Melody notes: {startMs, lengthMs, key, velocity}. */
        private final java.util.List<int[]> melody = new java.util.ArrayList<>();
        private long timeMs;
        private int bpm = -1;
        int notes;

        Midi(int program) {
            this.program = Math.max(0, Math.min(127, program));
        }

        public void tempo(int b) {
            if (bpm < 0 || notes == 0) {
                bpm = b;    // the groove follows the tempo the tune starts with
            }
        }

        public void note(int note, int octave, int ms, int onMs, int volume) {
            if (note != 0) {
                int key = Math.max(0, Math.min(127, 69 + (note - 10) + 12 * octave));
                int vel = Math.max(1, Math.min(127, 40 + volume * 6));
                melody.add(new int[] {(int) timeMs, Math.max(1, onMs), key, vel});
                notes++;
            }
            timeMs += ms;
        }

        byte[] smf() {
            java.util.List<long[]> ev = new java.util.ArrayList<>();   // {time, order, status, d1, d2}
            ev.add(new long[] {0, 0, 0xC0, program, -1});
            for (int[] n : melody) {
                ev.add(new long[] {n[0], 2, 0x90, n[2], n[3]});
                ev.add(new long[] {n[0] + n[1], 1, 0x80, n[2], 0});
            }
            if (arrange && !melody.isEmpty()) {
                accompany(ev);
            }
            ev.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
            ByteArrayOutputStream t = new ByteArrayOutputStream();
            // tempo 480000 us per quarter (125 BPM) with 480 ticks per quarter = 1 ms per tick
            t.write(0);
            t.write(0xFF);
            t.write(0x51);
            t.write(3);
            t.write(0x07);
            t.write(0x53);
            t.write(0x00);
            long last = 0;
            for (long[] e : ev) {
                writeVar(t, e[0] - last);
                last = e[0];
                t.write((int) e[2]);
                t.write((int) e[3]);
                if (e[4] >= 0) {
                    t.write((int) e[4]);
                }
            }
            writeVar(t, Math.max(0, timeMs - last));
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

        private void accompany(java.util.List<long[]> ev) {
            int beat = (int) Math.round(60000.0 / (bpm > 0 ? bpm : 63));
            int bar = beat * 4;
            int bars = (int) Math.max(1, (timeMs + bar / 2) / bar);
            int key = songKey();
            ev.add(new long[] {0, 0, 0xC1, BASS_PROGRAM, -1});
            int[] chord = null;
            for (int b = 0; b < bars; b++) {
                long t0 = (long) b * bar;
                for (int half = 0; half < 2; half++) {    // chords change every two beats
                    long h0 = t0 + half * beat * 2;
                    int[] c = chordFor(h0, h0 + beat * 2, key);
                    boolean same = c == null || (chord != null && c[0] == chord[0]);
                    if (c != null) {
                        chord = c;
                    }
                    if (chord != null) {
                        int root = 36 + chord[0];         // C2..B2
                        int fifth = root + 7 > 52 ? root - 5 : root + 7;
                        bass(ev, h0, beat * 2 - 40, half == 1 && same ? fifth : root);
                    }
                }
                for (int e8 = 0; e8 < 8; e8++) {         // hi-hat eighths, a little swing on the offbeat
                    long t = t0 + e8 * beat / 2 + (e8 % 2 == 1 ? beat / 12 : 0);
                    drum(ev, t, 42, e8 % 2 == 0 ? 46 : 34);
                }
                drum(ev, t0, 36, 64);
                drum(ev, t0 + beat * 2, 36, 56);
                drum(ev, t0 + beat, 37, 50);
                drum(ev, t0 + beat * 3, 37, 50);
            }
        }

        /** Major key (tonic pitch class) whose scale covers most of the melody, by duration. */
        private int songKey() {
            double[] w = new double[12];
            for (int[] n : melody) {
                w[n[2] % 12] += n[1];
            }
            int[] scale = {0, 2, 4, 5, 7, 9, 11};
            int best = 0;
            double bestScore = -1;
            for (int k = 0; k < 12; k++) {
                double sc = w[k] * 0.5 + w[(k + 7) % 12] * 0.25;   // tonic and dominant a bit more
                for (int d : scale) {
                    sc += w[(k + d) % 12];
                }
                if (sc > bestScore) {
                    bestScore = sc;
                    best = k;
                }
            }
            return best;
        }

        /** Best of the key's I ii iii IV V vi triads for the melody in [from, to): {root pc, minor}. */
        private int[] chordFor(long from, long to, int key) {
            double[] weight = new double[12];
            boolean any = false;
            for (int[] n : melody) {
                long s = Math.max(from, n[0]);
                long e = Math.min(to, n[0] + n[1]);
                if (e > s) {
                    weight[n[2] % 12] += e - s;
                    any = true;
                }
            }
            if (!any) {
                return null;
            }
            int[][] degrees = {{0, 0}, {5, 0}, {7, 0}, {9, 1}, {2, 1}, {4, 1}};   // I IV V vi ii iii
            double best = -1;
            int[] out = null;
            for (int i = 0; i < degrees.length; i++) {
                int root = (key + degrees[i][0]) % 12;
                int minor = degrees[i][1];
                double score = weight[root] * 1.5 + weight[(root + (minor == 1 ? 3 : 4)) % 12]
                        + weight[(root + 7) % 12] - 0.02 * i;    // ties go to the stronger degree
                if (score > best) {
                    best = score;
                    out = new int[] {root, minor};
                }
            }
            return out;
        }

        private static void bass(java.util.List<long[]> ev, long t, int len, int key) {
            ev.add(new long[] {t, 2, 0x91, key, 78});
            ev.add(new long[] {t + Math.max(60, len), 1, 0x81, key, 0});
        }

        private static void drum(java.util.List<long[]> ev, long t, int key, int vel) {
            ev.add(new long[] {t, 2, 0x99, key, vel});
            ev.add(new long[] {t + 60, 1, 0x89, key, 0});
        }

        private static void writeVar(ByteArrayOutputStream o, long v) {
            long buf = v & 0x7F;
            while ((v >>= 7) > 0) {
                buf <<= 8;
                buf |= ((v & 0x7F) | 0x80);
            }
            while (true) {
                o.write((int) (buf & 0xFF));
                if ((buf & 0x80) != 0) {
                    buf >>= 8;
                } else {
                    break;
                }
            }
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
