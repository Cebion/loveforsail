#!/bin/bash
# Desktop test run (WSL2/Linux). Usage: ./run.sh [WxH]   e.g. ./run.sh 720x960
# Keys: arrows move, Enter/Space = A, Backspace = B, PageUp = X (*), PageDown = Y (#),
#       Esc = Start (pause menu), Tab = Select (left soft key), 1/3/7/9 = diagonals
DIR="$(cd "$(dirname "$0")" && pwd)"
exec java \
  -Djava.library.path="$DIR/build" \
  -Dmidp.window="${1:-480x640}" \
  -Dmidp.rms.dir="$DIR/savedata" \
  -Djava.util.prefs.userRoot="$DIR/conf" \
  -cp "$DIR/build/lovesail-portmaster.jar" \
  LarryPortmaster "$DIR/larry_-_love_for_sail_240x320-15505.jar"
