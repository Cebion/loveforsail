# Larry – Love for Sail (J2ME) for PortMaster

Runs the original 240x320 J2ME game `larry_-_love_for_sail_240x320-15505.jar` **unmodified** on a
small Java MIDP 2.0 / CLDC shim (`javax.microedition.*` on headless AWT) with an SDL2 JNI bridge
for display and input. The image is pillarboxed and scaled with nearest-neighbour filtering.

The game jar is **not included**: you need your own copy.

## Layout

| Path | What |
|--|--|
| `shim/src/javax/microedition/` | MIDP shim: lcdui (Canvas/GameCanvas/Graphics/Image), media (MIDI via Gervill, WAV, silent fallback), rms (file-backed saves) |
| `shim/src/LarryPortmaster.java` | Launcher: loads the MIDlet, pumps SDL events, uploads frames |
| `shim/src/GameLoader.java`, `ResourcePatches.java` | Loads the game jar and patches resources in memory |
| `shim/resources/patches/text.txt` | Replacement button prompts for all 5 languages |
| `bridge/` | `libsdl2bridge.so` (JNI ↔ SDL2) |

## Build & run (desktop Linux / WSL2)

Needs a JDK (11+), gcc and the SDL2 development package.

```sh
./build.sh                     # -> build/lovesail-portmaster.jar, build/libsdl2bridge.so
cp /path/to/larry_-_love_for_sail_240x320-15505.jar .
./run.sh [WxH]                 # windowed, default 480x640
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
| `midp.window=WxH` | Windowed mode (default: fullscreen desktop) |
| `midp.width` / `midp.height` | Canvas size (default 240x320) |
| `midp.rms.dir` | Save directory (default `./savedata`) |
| `midp.soundfont` | Optional `.sf2` for MIDI |
| `midp.nopatch=true` | Disable prompt patches |
| `midp.dump.dir`, `midp.dump.every`, `midp.script` | Debug: frame dumps, scripted input |
