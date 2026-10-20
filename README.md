# Windows Key Labels for Mac

An IntelliJ plugin for people who use a **Windows keyboard on a Mac**.

On macOS, IntelliJ shows shortcuts with Mac symbols: `⇧⌘N`, `⌥⏎`, `⌃⌘F`. A Windows keyboard has no keys with those symbols,
and if you swapped Ctrl and Win in macOS, ⌘ is not even the key you would guess. This plugin shows the names printed on
your keys instead:

| IntelliJ shows | With the plugin |
|---|---|
| `⇧⌘N` | `Ctrl+Shift+N` |
| `⌥⏎` | `Alt+⏎` |
| `⌃⌘F` | `Win+Ctrl+F` |

(Examples for a keyboard with Ctrl and Win swapped in macOS.)

## When it helps

- You plugged a Windows keyboard into a Mac and keep translating ⌘ ⌃ ⌥ in your head.
- You remapped modifier keys in **System Settings → Keyboard → Keyboard Shortcuts → Modifier Keys**. The plugin reads that
  remapping and labels each modifier with the physical key that produces it.
- You switch between the MacBook keyboard and a Windows keyboard. With only Apple keyboards connected, the Mac symbols
  come back on their own.

## Install

1. Get `windows-key-labels-for-mac-<version>.zip` (see [Build from source](#build-from-source)).
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip.
3. Restart the IDE.

Works in IntelliJ IDEA and other JetBrains IDEs, version 2025.2 or later, on macOS.

## Settings

**Settings → Appearance & Behavior → Windows Key Labels for Mac**

- **Show Windows modifier names**: turns the plugin on or off.
- **Detect from the connected keyboard** (default): the plugin picks the labels from the keyboard that is plugged in.
- **Manual**: type the label for each Mac modifier yourself, or pick a preset (*Ctrl/Win swapped* or *No remapping*).
- **Current Mapping** shows the labels in use.

## Where you see it

Tooltips, right-click and popup menus, Find Action / Search Everywhere, the Keymap settings page, and hints in the editor.

Not changed:

- The macOS menu bar at the top of the screen. macOS draws it, not IntelliJ.
- Tips of the Day and onboarding screens, and Presentation Assistant.

## Good to know

- With the MacBook keyboard and an external keyboard both connected, the plugin assumes you type on the external one.
- Only remapping made in macOS System Settings is detected. If you remap with Karabiner-Elements or similar tools, use
  **Manual**.
- After unplugging a keyboard, the labels change the next time you switch to the IDE.
- IntelliJ has a built-in option that shows words instead of symbols: **Advanced Settings → "Use words instead of symbols
  for macOS keyboard shortcuts"**. It always says `Cmd` and does not follow a remapping.

## Build from source

Requires JDK 21.

```sh
./gradlew buildPlugin   # zip in build/distributions/
./gradlew runIde        # try it in a sandbox IDE
```

Add `-PplatformLocalPath="$HOME/Applications/IntelliJ IDEA.app"` to build against an installed IDE instead of downloading
one.
