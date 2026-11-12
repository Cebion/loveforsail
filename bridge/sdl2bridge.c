/* JNI bridge between the Java MIDP shim and SDL2: window, frame upload, keyboard events. */
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <SDL.h>

#define EVENT_QUIT 1
#define EVENT_KEYDOWN 2
#define EVENT_KEYUP 3

static SDL_Window *window;
static SDL_Renderer *renderer;
static SDL_Texture *texture;
static int game_w, game_h;

/* The JVM uses SIGSEGV/SIGBUS/SIGILL/SIGFPE internally (implicit null checks, safepoints). Some
 * CFW SDL2 builds install their own handlers for these during init and re-raise, which kills the
 * JVM, so the JVM's handlers are put back once SDL is up. */
static const int jvm_signals[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGQUIT, SIGINT, SIGTERM, SIGHUP};
#define NUM_JVM_SIGNALS (int)(sizeof(jvm_signals) / sizeof(jvm_signals[0]))
static struct sigaction saved_actions[NUM_JVM_SIGNALS];

static void save_jvm_signals(void)
{
    for (int i = 0; i < NUM_JVM_SIGNALS; i++)
        sigaction(jvm_signals[i], NULL, &saved_actions[i]);
}

static void restore_jvm_signals(void)
{
    for (int i = 0; i < NUM_JVM_SIGNALS; i++) {
        struct sigaction cur;
        sigaction(jvm_signals[i], NULL, &cur);
        if (cur.sa_sigaction != saved_actions[i].sa_sigaction) {
            fprintf(stderr, "[sdl2bridge] restored JVM handler for signal %d\n", jvm_signals[i]);
            sigaction(jvm_signals[i], &saved_actions[i], NULL);
        }
    }
}

JNIEXPORT jboolean JNICALL Java_SDL2Bridge_init(JNIEnv *env, jclass cls, jint gw, jint gh, jint ww, jint wh)
{
    Uint32 flags = SDL_WINDOW_SHOWN;

    (void)env;
    (void)cls;
    game_w = gw;
    game_h = gh;

    save_jvm_signals();
    SDL_SetHint(SDL_HINT_NO_SIGNAL_HANDLERS, "1");
    SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "0");
    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS) != 0) {
        fprintf(stderr, "[sdl2bridge] SDL_Init: %s\n", SDL_GetError());
        return JNI_FALSE;
    }

    if (ww <= 0 || wh <= 0) {
        SDL_DisplayMode dm;
        if (SDL_GetDesktopDisplayMode(0, &dm) == 0) {
            ww = dm.w;
            wh = dm.h;
        } else {
            ww = 640;
            wh = 480;
        }
        flags |= SDL_WINDOW_FULLSCREEN_DESKTOP;
    } else {
        flags |= SDL_WINDOW_RESIZABLE;
    }

    window = SDL_CreateWindow("Larry: Love for Sail", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED,
                              ww, wh, flags);
    if (!window) {
        fprintf(stderr, "[sdl2bridge] SDL_CreateWindow: %s\n", SDL_GetError());
        return JNI_FALSE;
    }

    renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);
    if (!renderer)
        renderer = SDL_CreateRenderer(window, -1, 0);
    if (!renderer) {
        fprintf(stderr, "[sdl2bridge] SDL_CreateRenderer: %s\n", SDL_GetError());
        return JNI_FALSE;
    }

    /* Keeps the 240x320 aspect ratio: pillarboxed and scaled to the screen height. */
    SDL_RenderSetLogicalSize(renderer, gw, gh);

    texture = SDL_CreateTexture(renderer, SDL_PIXELFORMAT_ARGB8888, SDL_TEXTUREACCESS_STREAMING, gw, gh);
    if (!texture) {
        fprintf(stderr, "[sdl2bridge] SDL_CreateTexture: %s\n", SDL_GetError());
        return JNI_FALSE;
    }
    SDL_SetTextureBlendMode(texture, SDL_BLENDMODE_NONE);

    SDL_ShowCursor(SDL_DISABLE);
    restore_jvm_signals();
    SDL_SetRenderDrawColor(renderer, 0, 0, 0, 255);
    SDL_RenderClear(renderer);
    SDL_RenderPresent(renderer);

    {
        SDL_RendererInfo info;
        int ow = 0, oh = 0;
        SDL_GetRendererInfo(renderer, &info);
        SDL_GetRendererOutputSize(renderer, &ow, &oh);
        fprintf(stderr, "[sdl2bridge] video=%s renderer=%s output=%dx%d game=%dx%d\n",
                SDL_GetCurrentVideoDriver(), info.name, ow, oh, gw, gh);
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_SDL2Bridge_updateFrame(JNIEnv *env, jclass cls, jintArray pixels, jint w, jint h)
{
    jint *px;

    (void)cls;
    if (!texture || w != game_w || h != game_h)
        return;

    px = (*env)->GetPrimitiveArrayCritical(env, pixels, NULL);
    if (!px)
        return;
    SDL_UpdateTexture(texture, NULL, px, w * 4);
    (*env)->ReleasePrimitiveArrayCritical(env, pixels, px, JNI_ABORT);

    SDL_SetRenderDrawColor(renderer, 0, 0, 0, 255);
    SDL_RenderClear(renderer);
    SDL_RenderCopy(renderer, texture, NULL, NULL);
    SDL_RenderPresent(renderer);
}

JNIEXPORT jint JNICALL Java_SDL2Bridge_pollEvent(JNIEnv *env, jclass cls)
{
    SDL_Event e;

    (void)env;
    (void)cls;
    while (SDL_PollEvent(&e)) {
        switch (e.type) {
        case SDL_QUIT:
            return EVENT_QUIT << 16;
        case SDL_KEYDOWN:
            if (e.key.repeat)
                break;
            return (EVENT_KEYDOWN << 16) | (e.key.keysym.scancode & 0xFFFF);
        case SDL_KEYUP:
            return (EVENT_KEYUP << 16) | (e.key.keysym.scancode & 0xFFFF);
        default:
            break;
        }
    }
    return 0;
}

JNIEXPORT void JNICALL Java_SDL2Bridge_destroy(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    if (texture)
        SDL_DestroyTexture(texture);
    if (renderer)
        SDL_DestroyRenderer(renderer);
    if (window)
        SDL_DestroyWindow(window);
    texture = NULL;
    renderer = NULL;
    window = NULL;
    SDL_Quit();
}
