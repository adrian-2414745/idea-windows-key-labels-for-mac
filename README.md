# Windows Shortcut Symbols

IntelliJ plugin for using a Windows keyboard on a Mac. IntelliJ on macOS shows shortcuts as `⇧⌘N`; this plugin shows them
with the names printed on a Windows keyboard, e.g. `Ctrl+Shift+N`.

Labels follow the keyboard that is plugged in, including any macOS modifier remapping. With only the MacBook keyboard
connected, the macOS symbols come back.

## Install

1. Build the zip (see [Build](#build)) or take `build/distributions/windows-shortcut-symbols-<version>.zip`.
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip.
3. Restart the IDE (required on install, update and removal).

Supported: IntelliJ-based IDEs 2025.2 (build 252) and later, on macOS. On other systems the plugin does nothing.

## Settings

**Settings → Appearance & Behavior → Windows Shortcut Symbols**

- **Show Windows modifier names**: on/off.
- **Detect from the connected keyboard** (default): labels come from the connected keyboard. The line below shows what
  was detected; **Detect again** re-runs detection.
- **Always use the manual labels**: ignore detection.
- **Manual labels**: one label per macOS modifier, also used when detection cannot decide. Presets:
  - *Ctrl/Win swapped in macOS*: ⌘ = Ctrl, ⌃ = Win, ⌥ = Alt, ⇧ = Shift
  - *No remapping*: ⌘ = Win, ⌃ = Ctrl, ⌥ = Alt, ⇧ = Shift

Modifiers are written in Windows order: `Win+Ctrl+Alt+Shift+<key>`. Other keys keep the macOS text (`⏎`, `⌫`, `⎋`, arrows).

## Keyboard detection

Sources:

- `hidutil list --matching '{"DeviceUsagePage":1,"DeviceUsage":6}'`: connected keyboards.
- `hidutil list --matching '{"DeviceUsagePage":1,"DeviceUsage":2}'`: connected mice.
- `defaults -currentHost export -g -`: the `com.apple.keyboard.modifiermapping.<vendorId>-<productId>-0` entries written by
  **System Settings → Keyboard → Keyboard Shortcuts → Modifier Keys**.

Rules:

| Connected | Result |
|---|---|
| Only built-in, virtual or Apple (vendor `0x5AC`) keyboards | macOS symbols |
| Remapped external keyboard(s), all with the same mapping | Labels from the mapping |
| Remapped external keyboards with different mappings | Manual labels |
| External keyboard(s) without remapping | ⌘ = Win, ⌃ = Ctrl, ⌥ = Alt, ⇧ = Shift |
| Only devices that are also mice (gaming mice register as keyboards), not remapped | macOS symbols |
| Detection failed (command error, unexpected output) | Manual labels; warning in `idea.log` |

A label is the name of the physical key that produces the modifier. If physical Ctrl sends ⌘, ⌘ is shown as `Ctrl`.
Several keys for one modifier are joined (`Ctrl/Caps Lock`). A modifier that no key produces keeps its symbol.

Detection runs at startup, when the IDE window gets focus (at most every 5 s), and when the settings page opens. Each run
takes about 0.1 s, in the background.

## Limitations

- The macOS menu bar is drawn by macOS and keeps its symbols.
- Tips of the Day / onboarding key caps and Presentation Assistant call `MacKeymapUtil` directly and keep the symbols.
- macOS does not tell which keyboard a key press came from. With the built-in and an external keyboard both present, the
  external one is assumed.
- Remapping by Karabiner-Elements, `hidutil property --set UserKeyMapping` or BetterTouchTool is not detected. Use manual
  labels.
- An unplugged keyboard is noticed on the next IDE focus, not immediately.
- A non-Apple keyboard with a built-in touchpad and no remapping is treated as a mouse.

## How it works

IntelliJ builds all shortcut text through `KeymapUtil`, which delegates to a `KeymapTextContext` held in a
`private static final` field (`defaultKeymapTextContext` in 2026.x, `ourDefaultKeymapTextContext` in 2025.x). There is no
extension point for it. At `AppLifecycleListener.appFrameCreated` the plugin finds that field by type and replaces its
value, with `sun.misc.Unsafe`, by `WindowsKeymapTextContext`, a subclass that overrides `getKeystrokeText`, `getKeyText`
and `getMouseShortcutText`. When the plugin is off or the keyboard is a Mac keyboard, the subclass calls the platform code.

The swap is done before the first frame because JIT-compiled code may keep the old instance. The plugin is marked
`require-restart="true"` for the same reason.

A built-in alternative exists: **Advanced Settings → "Use words instead of symbols for macOS keyboard shortcuts"**
(`ide.macos.disable.native.shortcut.symbols`). It shows text, but always calls ⌘ `Cmd` and cannot follow a remapping.

## Code

`src/main/kotlin/com/adriansalajan/winsymbols/`:

| File | Role |
|---|---|
| `WinSymbolsStartup.kt` | Installs the text context at startup; re-detects on IDE focus |
| `KeymapTextContextInstaller.kt` | Finds and replaces `KeymapUtil`'s context field |
| `WindowsKeymapTextContext.kt` | `KeymapTextContext` subclass that writes Windows labels |
| `ModifierLabels.kt` | Label set, presets, `WindowsShortcutFormatter` (modifier order, `+` joining) |
| `KeyboardDetector.kt` | App service: runs `hidutil` / `defaults` in the background, holds the last `Detection` |
| `KeyboardDetection.kt` | Parsers for `hidutil` and plist output; `KeyboardMappingResolver` (the rules above) |
| `WinSymbolsSettings.kt` | Persisted options (`windowsShortcutSymbols.xml`); picks detected, manual or no labels |
| `WinSymbolsConfigurable.kt` | Settings page |

Tests in `src/test/kotlin/...`; `src/test/resources/fixtures/` holds real `hidutil` and `defaults` output from a MacBook with
a remapped Logitech keyboard and a Logitech mouse.

## Build

Requires JDK 21. Gradle comes from the wrapper.

```sh
./gradlew build buildPlugin      # compile, test, zip -> build/distributions/
./gradlew runIde                 # start a sandbox IDE with the plugin
```

The target IDE (`platformVersion` in `gradle.properties`) is downloaded. To use an installed IDE instead:

```sh
./gradlew build -PplatformLocalPath="$HOME/Applications/IntelliJ IDEA.app"
```

or set `platformLocalPath` in `~/.gradle/gradle.properties`.
