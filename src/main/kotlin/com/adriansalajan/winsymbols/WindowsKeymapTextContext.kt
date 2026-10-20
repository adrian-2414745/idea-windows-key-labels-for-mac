package com.adriansalajan.winsymbols

import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.PressureShortcut
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapTextContext
import javax.swing.KeyStroke

/**
 * Replaces macOS modifier symbols (⌘⌃⌥⇧) with Windows names. Non-modifier keys keep the platform text.
 *
 * [labels] returns null when the plugin is off; the platform behavior is used then.
 */
class WindowsKeymapTextContext(private val labels: () -> ModifierLabels?) : KeymapTextContext() {

  override fun getKeystrokeText(accelerator: KeyStroke?): String {
    val labels = labels() ?: return super.getKeystrokeText(accelerator)
    if (accelerator == null) return ""
    return WindowsShortcutFormatter.keystrokeText(accelerator, getKeyText(accelerator.keyCode), labels)
  }

  override fun getKeyText(code: Int): String {
    val labels = labels() ?: return super.getKeyText(code)
    return WindowsShortcutFormatter.modifierKeyLabel(code, labels) ?: super.getKeyText(code)
  }

  override fun getMouseShortcutText(shortcut: MouseShortcut): String {
    val labels = labels()
    if (labels == null || shortcut is PressureShortcut) return super.getMouseShortcutText(shortcut)
    // Same messages as the platform, which throws for clickCount > 2
    val resource = when {
      shortcut.button == MouseShortcut.BUTTON_WHEEL_UP -> "mouse.wheel.rotate.up.shortcut.text"
      shortcut.button == MouseShortcut.BUTTON_WHEEL_DOWN -> "mouse.wheel.rotate.down.shortcut.text"
      shortcut.clickCount < 2 -> "mouse.click.shortcut.text"
      shortcut.clickCount < 3 -> "mouse.double.click.shortcut.text"
      else -> return super.getMouseShortcutText(shortcut)
    }
    val modifiers = WindowsShortcutFormatter.modifiersText(shortcut.modifiers, labels)
    return KeyMapBundle.message(resource, if (modifiers.isEmpty()) "" else "$modifiers+", shortcut.button)
  }
}
