# Builds libsdl2bridge.so. JAVA_HOME must point at a JDK (for jni.h).
JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(which javac))))
CC ?= gcc
CFLAGS ?= -O2
CFLAGS += -fPIC -Wall -Wextra -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux $(shell sdl2-config --cflags)
LDLIBS = $(shell sdl2-config --libs)

libsdl2bridge.so: sdl2bridge.c
	$(CC) $(CFLAGS) -shared -o $@ $< $(LDLIBS) -Wl,-soname,libsdl2bridge.so

clean:
	rm -f libsdl2bridge.so
