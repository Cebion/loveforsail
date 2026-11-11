#!/bin/bash
# Desktop test run (WSL2/Linux). Works with any phone version of the game.
# Usage: ./run.sh [game.jar] [scale|WxH]     e.g. ./run.sh   ./run.sh larry_176x208.jar 3
# Without a jar argument the first game jar in this folder (or games/) is used.
# Keys: arrows move, Enter/Space = A, Backspace = B, PageUp = X (*), PageDown = Y (#),
#       Esc = Start (pause menu), Tab = Select (left soft key), 1/3/7/9 = diagonals
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR=""
if [[ "$1" == *.jar ]]; then
  JAR="$1"
  shift
else
  for f in "$DIR"/*.jar "$DIR"/games/*.jar; do
    [ -f "$f" ] && JAR="$f" && break
  done
fi
if [ ! -f "$JAR" ]; then
  echo "No game jar found: put your copy of the game (any version) next to run.sh" >&2
  exit 1
fi
exec java \
  -Djava.library.path="$DIR/build" \
  -Dmidp.window="${1:-2}" \
  -Dmidp.rms.dir="$DIR/savedata/$(basename "$JAR" .jar)" \
  -Djava.util.prefs.userRoot="$DIR/conf" \
  -cp "$DIR/build/lovesail-portmaster.jar" \
  LarryPortmaster "$JAR"
