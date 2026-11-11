# Larry – Love for Sail (J2ME) for PortMaster

Runs the original J2ME game **unmodified**, in any of its phone versions (128x160, 176x208,
176x220, 240x320, ...; any file name), on a
small Java MIDP 2.0 / CLDC shim (`javax.microedition.*` on headless AWT) with an SDL2 JNI bridge
for display and input. The image is pillarboxed and scaled with nearest-neighbour filtering.

The game jar is **not included**: you need your own copy.

## Game versions

The game was released in many builds, one per phone screen size and maker, with different file
names. The launcher works out what each build needs (`shim/src/GameProfile.java`):

- **Screen size**, in this order: `-Dmidp.width/-Dmidp.height`, a `WxH` in the file name, the
  manifest or a `.jad` next to the jar (`Nokia-MIDlet-Target-Display-Size`, `MIDlet-Display-Size`, ...),
  full-screen PNGs inside the jar, then `Nokia-MIDlet-Original-Display-Size`. If nothing matches,
  it falls back to 240x320.
- **Soft keys**: -6/-7 (Nokia, Sony Ericsson, Samsung), or Motorola -21/-22 / Siemens -1/-4 when
  the game's code uses those. Override with `-Dmidp.softkeys=L,R`.
- **Phone APIs**: MIDP 2.0 (GameCanvas) and MIDP 1.0-style Canvas games, MMAPI sound, RMS saves,
  plus the Nokia UI API (`FullCanvas`, `DirectGraphics`, `DeviceControl`, `Sound`) used by Series 40
  builds. If a build needs something that is still missing, the log says
  `[host] this game version uses an API the shim does not provide: ...`.

The startup log shows what was picked, e.g.
`[host] larry.jar: screen 240x320 (manifest Nokia-MIDlet-Target-Display-Size), soft keys -6/-7 (...)`.

The button-prompt patches (see below) are tied to the 240x320 build's files. Other builds keep
their original prompts unless their text tables match.

## Layout

| Path | What |
|--|--|
| `shim/src/javax/microedition/` | MIDP shim: lcdui (Canvas/GameCanvas/Graphics/Image), media (MIDI via Gervill, WAV, silent fallback), rms (file-backed saves) |
| `shim/src/com/nokia/mid/` | Nokia UI and Sound API |
| `shim/src/LarryPortmaster.java` | Launcher: loads the MIDlet, pumps SDL events, uploads frames |
| `shim/src/GameProfile.java` | Detects screen size and soft keys of the game version |
| `shim/src/GameLoader.java`, `ResourcePatches.java` | Loads the game jar and patches resources in memory |
| `shim/resources/patches/text.txt` | Replacement button prompts for all 5 languages |
| `bridge/` | `libsdl2bridge.so` (JNI ↔ SDL2) |

## Build & run (desktop Linux / WSL2)

Needs a JDK (11+), gcc and the SDL2 development package.

```sh
./build.sh                     # -> build/lovesail-portmaster.jar, build/libsdl2bridge.so
cp /path/to/your/larry-version.jar .   # any version, any name
./run.sh                       # first jar in this folder (or games/), window at 2x
./run.sh some_other_version.jar 3      # pick a jar, 3x window (or WxH)
```

The jar is plain Java (`--release 11`). Only `libsdl2bridge.so` has to be built for each
architecture (`make -C bridge`, with `JAVA_HOME` set to a JDK that has `include/jni.h`).

## Controls

The gamepad stands in for the phone keypad, so every in-game action keeps working:

```
L1 = 1    Up = 2     R1 = 3
Left = 4  A = 5      Right = 6
L2 = 7    Down = 8   R2 = 9
          B = 0      X = * (inventory)   Y = # (help)
Start = right soft key (pause menu)      Select = left soft key
```

Keyboard (what gptokeyb should send): arrows = D-pad, Enter/Space = A, Backspace = B,
PageUp = X, PageDown = Y, Esc = Start, Tab = Select, 1/3/7/9 = L1/R1/L2/R2.

## Prompt patches

At load time the port rewrites the game's "press 5" style prompts (help screen, tutorial,
minigame instructions) into gamepad button names. It also redraws the digit glyphs in the
Spank-a-Monkey minigame (`biminig_button.png`) as button labels. This happens in memory and the
jar stays untouched. A table is only patched when it has the expected string count. Pass
`-Dmidp.nopatch=true` to see the original prompts.

## System properties

| Property | Meaning |
|--|--|
| `midp.window=WxH` or `N` | Windowed mode, `N` = scale of the game size (default: fullscreen desktop) |
| `midp.width` / `midp.height` | Canvas size (default: detected) |
| `midp.softkeys=L,R` | Soft key codes (default: detected) |
| `midp.rms.dir` | Save directory (default `./savedata`; `run.sh` keeps one per jar) |
| `midp.soundfont` | Optional `.sf2` for MIDI |
| `midp.nopatch=true` | Disable prompt patches |
| `midp.dump.dir`, `midp.dump.every`, `midp.script` | Debug: frame dumps, scripted input |

## License

The port (shim, launcher, bridge) is MIT licensed, see `LICENSE`. The game itself is not part of
this repository and keeps its original copyright.
