# RClient

A premium-feeling, dark, animated **client-side visuals and cosmetics** mod for Minecraft **1.21.11** (Fabric, Java 21).

RClient never sends or edits packets and never touches movement, combat or any gameplay value. Every module is a
visual or local-audio effect only. Attack events are observed and always passed through unchanged.

> **Build status:** compiles against 1.21.11 (Mojang mappings) on your machine. The in-world visuals were written
> without being able to launch the game, so treat sky / wings / hand flames as "needs a look in game". Class names for
> Fabric API and Minecraft were taken from `gradlew dumpApi` (writes `api-dump.txt`).

## Build

Based on the Fabric template for 1.21.11 (official Mojang mappings, Loom 1.18, Gradle 9.7.1 wrapper included).

1. Install JDK 25 (`java -version` should say 25; the template's Loom needs Java 25 to run Gradle. The mod itself still targets Java 21).
2. In the project folder run:
   * Windows: `gradlew build` or `gradlew runClient`
   * macOS / Linux: `./gradlew build` or `./gradlew runClient`
3. The mod jar ends up in `build/libs/` (use the one without `-sources`).
4. Optional: put `Inter-Regular.ttf`, `Inter-SemiBold.ttf`, `Inter-Bold.ttf` in
   `src/main/resources/assets/rclient/font/`. Without them the JVM SansSerif font is used.

Layout: all code is client-only and lives in `src/client/java`; `src/main/resources` holds `fabric.mod.json` and the assets.

## Main menu

Backdrop = one baked texture (30 Hz). Player = one software-rendered texture with your real skin (UUID, Mojang session
server, cached in `.minecraft/rclient/skins`, Steve/Alex fallback, never any cosmetics). It walks in from the background
the first time the menu opens each session, looks at the cursor, and can be dragged to rotate.

## Using it

| Action | Default |
|---|---|
| Open the ClickGUI (in-game) / main-menu "Client" | Right Shift |
| Emote wheel (hold, release to play) | B |
| In the GUI: toggle / expand settings | Left click / right click a card, or Space / Enter after Tab-focusing it |
| Reset a setting | Right-click it |

Configs live in `.minecraft/rclient/configs/<name>.json` (Configs tab: save, load, rename, delete). The `default`
profile is auto-saved when you close the GUI and on exit.

## What is in it

**Rendering / systems:** CPU-baked SDF rounded rects (per-corner radius, gradient fill + border, soft shadows/glows,
cached per GUI scale), custom TTF renderer with 4 sub-pixel phases, delta-time animator (easing, springs,
AnimatedFloat/Color/Vec2, Tween), typed settings (bool/number/mode/color+gradient+rainbow/keybind), favorites,
JSON config profiles, themeable accent with presets, toast notifications.

**Screens:** animated main menu (curtains, floating shapes, centered logo, text buttons with centre-out underline,
live 3D preview wearing your cosmetics with drag-to-rotate, account dropdown), ClickGUI (sliding tab pill, search,
staggered 2-column card grid, inline settings with animated height, colour picker with gradient, inertia scroll with
rubber band, keyboard navigation, footer hints), drag-and-drop HUD editor with snapping.

### Module status

| Module | State |
|---|---|
| Skies: Galaxy Stars (live gradient, nebula, galactic band, glowing stars, constellation lines) plus image/procedural styles, drawn as a fog-free vertex-colour dome (textured layers get hidden by distance fog) | rewritten, check in game |
| Trails (parallel bloom ribbons + rotating ground glyph) | implemented, world hook unverified |
| Cosmetics: Wings (bat/crystal/angel), Horns, Flame Hat, Halo, Orbiting Items, Pet, Cape | implemented (menu preview + world), world hook unverified |
| HitFX / JumpFX / KillFX / Pearl Particles | implemented, world hook unverified |
| Line Glyphs | implemented, world hook unverified |
| Emotes (wheel UI + 8 poses) | wheel implemented; model posing via a `require = 0` mixin, unverified |
| Hit Sounds (vanilla sounds, local) | implemented |
| GUI Animation (vanilla screens) | implemented via Fabric screen events |
| Keystrokes, Keybinds list, Info bar, Hotbar, Health, Hunger, notifications, Editor | implemented |
| Hand Effects | screen-space flames rising from under the hand, streaming against your mouse movement (the hand model is drawn by the game before the HUD) |
| Motion Blur | **approximation**: screen-edge streaks from camera speed, not true accumulation blur |
| Knife Models | menu preview only; in-hand replacement not hooked |
| Item Physics, No Block Rotation, Player Model | **settings + GUI only**, render hooks not written yet |

Real shader-based rendering (GLSL SDF, kawase blur, post-processing for true motion blur) is **not** used. All
shapes and sky effects are CPU-baked into textures. That was a deliberate choice: it avoids the 1.21.11 render-pipeline
registration API, which could not be checked offline. It can be upgraded later.

## Known risk areas (fix these first if the build complains)

* `render/WorldDraw.java` – `WorldRenderEvents`, `RenderType.debugQuads/lightning/eyes/entityTranslucent*`,
  vertex builder chain. Everything in-world goes through this one file.
* `gui/RScreen.java` – input signatures (`MouseButtonEvent`, `KeyEvent`, `CharacterEvent`). One file isolates them.
* `render/TextureFactory.java` – `DynamicTexture` constructor.
* `render/RenderUtil.java`, `hud/*` – `DrawContext.drawTexture(RenderPipelines.GUI_TEXTURED, …)` and the 2D
  `getMatrices().pushMatrix()` stack used by 1.21.6+.
* `mixin/PlayerEntityModelMixin.java` – `require = 0`, will silently disable emote posing if the target changed.
* `hud/HudManager.java` – `HudElementRegistry` / `VanillaHudElements` names.

## Assets

`assets/rclient/textures/sky/*` are your four sky images upscaled 4×. `textures/particle/*` are your particle images
converted to transparent PNGs (`kenney/` = 80 cells from the Kenney sheet, CC0; `sprites/` = 51 icons, currently not
referenced by code). `textures/gui/logo*.png` is the RClient.

**Licensing:** the smoke particle came from a stock-site preview and still has a faint watermark; the sky and
particle sources are of unknown license. Replace them before publishing the mod.
