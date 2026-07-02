# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

"Dima Advanture" (`app` module, package `com.dima`, namespace `com.dima`) is a single-Activity Android game written in Kotlin. It's an endless side-scrolling runner/jumper where the player collects beer and vodka items and shoots bottles, built with a custom `SurfaceView` game loop (not a game engine).

## Common commands

Build and tooling is standard Gradle/AGP; run from the project root (use `./gradlew` on POSIX shells, `.\gradlew.bat` or `./gradlew` via Git Bash on Windows).

- Build debug APK: `./gradlew assembleDebug`
- Install on connected device/emulator: `./gradlew installDebug`
- Run unit tests (JVM, `app/src/test`): `./gradlew testDebugUnitTest`
- Run a single unit test: `./gradlew testDebugUnitTest --tests "com.dima.ExampleUnitTest"`
- Run instrumented tests (`app/src/androidTest`, requires a device/emulator): `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lint`
- Clean: `./gradlew clean`

There is currently no CI config, README, or linter config beyond the default Android Gradle Plugin lint checks.

## Architecture

The app has almost no layers — it's intentionally minimal:

- `MainActivity.kt` — a `ComponentActivity` that skips Compose UI/XML layouts for the actual game: it constructs `GameView` in `onCreate` and calls `setContentView(gameView)` directly. Compose (`Greeting`/`DimaAdvantureTheme`) exists only as unused Android Studio template boilerplate. Activity lifecycle (`onResume`/`onPause`) drives `gameView.resume()`/`gameView.pause()` to start/stop the game thread.
- `GameView.kt` — the entire game: a `SurfaceView` implementing `Runnable` that owns its own background `Thread` for a fixed-step loop (`update()` → `draw()` → `control()`, `Thread.sleep(17)` per frame, no delta-time). All game state (player position/velocity, platforms, collectibles, bullets, UI button hitboxes) lives as mutable fields/lists directly on `GameView` — there's no ECS, no separate model/state classes, no scene graph.
  - **State**: player physics (gravity/jump), procedurally generated platforms (`platforms`), collectible items (`regularBeers`, `flyingVodka` with sinusoidal motion via `FlyingBeer`), player-fired `bullets`.
  - **Update loop** (`update()`): applies gravity, advances bullets, procedurally spawns new platforms/beer just past the right edge of the screen, scrolls all world objects left at a constant `worldSpeed`, resolves simple `RectF`-based AABB collisions for platform landing and item pickup, and resets the run if the player falls below the screen (`resetGame()`).
  - **Rendering** (`draw()`): locks the `SurfaceHolder` canvas and draws background, platforms, sprites (bitmaps loaded once from `res/drawable` via `BitmapFactory`), and a simple on-screen HUD/touch UI (`drawUI()` — jump/shoot button rectangles drawn each frame).
  - **Input**: `onTouchEvent` hit-tests `btnJump`/`btnShoot` rectangles against touch-down coordinates; there's no gesture library or input abstraction.
  - Sizing is handled in `onSizeChanged`, which also does one-time layout of UI buttons and the initial ground platform based on the actual screen dimensions (the activity is locked to landscape via the manifest).
- `ui/theme/` — standard Compose Material3 theme scaffolding (`Color.kt`, `Theme.kt`, `Type.kt`), unused by the actual game but wired up for the vestigial Compose preview in `MainActivity.kt`.

When extending gameplay, follow the existing pattern in `GameView.kt`: add new mutable object lists, spawn/update/collide/draw them inline within the existing `update()`/`draw()` methods rather than introducing new abstractions — the codebase is currently a single-file game loop by design.

Code comments and some in-game text are in Russian.
