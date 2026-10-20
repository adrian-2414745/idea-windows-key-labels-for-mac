package com.adriansalajan.winsymbols

import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/** Text shown for each macOS modifier. */
data class ModifierLabels(
  val command: String,
  val control: String,
  val option: String,
  val shift: String,
) {
  companion object {
    /** Windows keyboard with Ctrl and Win swapped in macOS (physical Ctrl sends ⌘). */
    val SWAPPED = ModifierLabels(command = "Ctrl", control = "Win", option = "Alt", shift = "Shift")

    /** Windows keyboard without macOS modifier remapping (physical Win sends ⌘). */
    val STANDARD = ModifierLabels(command = "Win", control = "Ctrl", option = "Alt", shift = "Shift")
  }
}

/** Builds Windows-style shortcut text such as `Ctrl+Shift+N`. */
object WindowsShortcutFormatter {
  // Windows convention: Win+Ctrl+Alt+Shift. Unknown labels go last, in macOS order.
  private val ORDER = listOf("Win", "Ctrl", "Alt", "Shift")

  @Suppress("DEPRECATION")
  fun modifiersText(modifiers: Int, labels: ModifierLabels): String {
    val present = buildList {
      if (modifiers and (InputEvent.CTRL_DOWN_MASK or InputEvent.CTRL_MASK) != 0) add(labels.control)
      if (modifiers and (InputEvent.ALT_DOWN_MASK or InputEvent.ALT_MASK) != 0) add(labels.option)
      if (modifiers and (InputEvent.SHIFT_DOWN_MASK or InputEvent.SHIFT_MASK) != 0) add(labels.shift)
      if (modifiers and (InputEvent.ALT_GRAPH_DOWN_MASK or InputEvent.ALT_GRAPH_MASK) != 0) add("AltGr")
      if (modifiers and (InputEvent.META_DOWN_MASK or InputEvent.META_MASK) != 0) add(labels.command)
    }
    return present
      .sortedBy { label -> ORDER.indexOf(label).takeIf { it >= 0 } ?: ORDER.size }
      .joinToString("+")
  }

  fun keystrokeText(stroke: KeyStroke, keyText: String, labels: ModifierLabels): String {
    val modifiers = modifiersText(stroke.modifiers, labels)
    return if (modifiers.isEmpty()) keyText else "$modifiers+$keyText"
  }

  /** Label for a modifier pressed as the key itself (e.g. "Double Shift"), or null for other keys. */
  fun modifierKeyLabel(keyCode: Int, labels: ModifierLabels): String? = when (keyCode) {
    KeyEvent.VK_META -> labels.command
    KeyEvent.VK_CONTROL -> labels.control
    KeyEvent.VK_ALT -> labels.option
    KeyEvent.VK_SHIFT -> labels.shift
    else -> null
  }
}
