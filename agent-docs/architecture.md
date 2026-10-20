# Architecture

## Folder structure

```
src/main/kotlin/io/github/adrian2414745/winkeylabels/
  WinSymbolsStartup.kt            startup hook (install text context, first detection), re-detect on IDE focus
  KeymapTextContextInstaller.kt   replaces the platform's shortcut-text object
  WindowsKeymapTextContext.kt     shortcut text with Windows labels
  ModifierLabels.kt               label sets + formatter (order, "+" joining)
  KeyboardDetector.kt             app service: runs hidutil/defaults off the EDT, holds the last result
  KeyboardDetection.kt            parsers + resolver (pure, unit-tested)
  WinSymbolsSettings.kt           persisted options; decides which labels are active
  WinSymbolsConfigurable.kt       settings page
src/main/resources/META-INF/plugin.xml
src/test/resources/fixtures/      real hidutil / defaults output from a MacBook + Logitech keyboard and mouse
```

## Shortcut text

All IntelliJ shortcut text goes through `KeymapUtil`, which delegates to a `KeymapTextContext` in a `private static final`
field. There is no extension point, so the plugin replaces the field's value with a subclass.

- **Field found by type, not name.** It is `ourDefaultKeymapTextContext` in 2025.2 and `defaultKeymapTextContext` in 2026.x.
- **Written with `sun.misc.Unsafe`.** Reflection cannot set a `static final` field. IntelliJ 2026.x starts JBR 25 with
  `--sun-misc-unsafe-memory-access=allow`, so there is no warning.
- **Installed at `appFrameCreated`, plugin is `require-restart`.** The JIT treats static finals as constants; code compiled
  before the swap keeps the old object. Installing early and never swapping at runtime avoids mixed output. On/off and
  label changes go through a lookup inside the subclass instead.
- **Subclass falls back to `super`** when the plugin is off, not on macOS, or only Apple keyboards are connected, so
  behavior is then exactly the platform's.
- **Only modifiers change.** `getModifiersText` is package-private and cannot be overridden, so the subclass rebuilds
  keystroke and mouse text from public methods and keeps the platform's key text (`⏎`, `⌫`).
- **Not covered:** the native macOS menu bar (drawn by macOS), and code calling `MacKeymapUtil` directly (tips/onboarding
  key caps, Presentation Assistant).
- `WindowsKeymapTextContextTest` exercises the swap against the real platform; it is the canary for platform changes.

## Keyboard detection

- **`hidutil list --matching …`**, not `ioreg`: about 50 ms vs 0.6 s and 6 MB of output. Its tables are fixed-width, so
  columns are located by header position. One call for keyboards (usage 1/6), one for mice (1/2).
- **`defaults -currentHost export -g -`** for `com.apple.keyboard.modifiermapping.<vendor>-<product>-<n>`: XML output, no
  plist library needed. The trailing `<n>` is not the country code; entries are matched on vendor and product only.
  Usages appear as both `<integer>` and `<real>`.
- **Resolver rules and why:**
  - Built-in, virtual and Apple-vendor keyboards are ignored: their key caps match the Mac symbols.
  - Remapped keyboards win over unremapped ones: an unremapped device is often a mouse or receiver with a keyboard
    interface.
  - Unremapped devices that are also mice are ignored: gaming mice register a keyboard interface, and would otherwise
    turn the labels on with only the MacBook keyboard in use.
  - Conflicting mappings give `Undecided`, which falls back to the manual labels.
- macOS does not say which keyboard produced a key event, so the result is a best guess about the connected set.
- Detection runs at startup, on IDE focus (throttled to 5 s) and when the settings page opens. No IOKit hot-plug
  callbacks: native code for little gain.

## Threading

Shortcut text is computed on the EDT, often. `WindowsKeymapTextContext` and `WinSymbolsSettings.activeLabels()` only read
fields (`serviceIfCreated`, volatile result); processes run in `KeyboardDetector` on `Dispatchers.IO`.

## Settings page

The mapping fields always show the labels in effect for the page state (detected, manual, or Mac symbols), so they are
not bound to the stored labels. A manual draft is kept separately and saved on Apply whatever the mode, because the manual
labels are also the detection fallback.

## Compatibility

- `sinceBuild = 252`, `jvmTarget = 21`, Kotlin `apiVersion = 2.2` (stdlib bundled with 2025.2). Compiled against 2026.2,
  so new platform APIs must also exist in 2025.2.
- Platform `inline` Kotlin functions are JVM 25 bytecode in 2026.2 and cannot be inlined into JVM 21 code; use non-inline
  overloads (e.g. `ButtonsGroup.bind(MutableProperty, Class)`).
