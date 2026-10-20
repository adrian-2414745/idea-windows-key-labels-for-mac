package com.adriansalajan.winsymbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class WindowsShortcutFormatterTest {

  private fun text(stroke: String, labels: ModifierLabels = ModifierLabels.SWAPPED): String {
    val keyStroke = KeyStroke.getKeyStroke(stroke)
    return WindowsShortcutFormatter.keystrokeText(keyStroke, KeyEvent.getKeyText(keyStroke.keyCode), labels)
  }

  @Test
  fun `command maps to Ctrl when Ctrl and Win are swapped`() {
    assertEquals("Ctrl+Shift+N", text("meta shift N"))
  }

  @Test
  fun `control maps to Win when Ctrl and Win are swapped`() {
    assertEquals("Win+N", text("control N"))
  }

  @Test
  fun `command maps to Win without remapping`() {
    assertEquals("Win+C", text("meta C", ModifierLabels.STANDARD))
    assertEquals("Ctrl+C", text("control C", ModifierLabels.STANDARD))
  }

  @Test
  fun `modifiers use Windows order Win Ctrl Alt Shift`() {
    assertEquals("Win+Ctrl+Alt+Shift+K", text("meta control alt shift K"))
    assertEquals("Win+Ctrl+Alt+Shift+K", text("meta control alt shift K", ModifierLabels.STANDARD))
  }

  @Test
  fun `custom labels go after known labels in macOS order`() {
    val labels = ModifierLabels(command = "Cmd", control = "Ctrl", option = "Opt", shift = "Shift")
    assertEquals("Ctrl+Shift+Opt+Cmd+K", text("meta control alt shift K", labels))
  }

  @Test
  fun `key without modifiers has no separator`() {
    assertEquals("F5", text("F5"))
  }

  @Test
  fun `old-style modifier masks are recognized`() {
    @Suppress("DEPRECATION")
    val modifiers = java.awt.event.InputEvent.META_MASK or java.awt.event.InputEvent.SHIFT_MASK
    assertEquals("Ctrl+Shift", WindowsShortcutFormatter.modifiersText(modifiers, ModifierLabels.SWAPPED))
  }

  @Test
  fun `modifier keys get labels and other keys do not`() {
    val labels = ModifierLabels.SWAPPED
    assertEquals("Ctrl", WindowsShortcutFormatter.modifierKeyLabel(KeyEvent.VK_META, labels))
    assertEquals("Win", WindowsShortcutFormatter.modifierKeyLabel(KeyEvent.VK_CONTROL, labels))
    assertEquals("Alt", WindowsShortcutFormatter.modifierKeyLabel(KeyEvent.VK_ALT, labels))
    assertEquals("Shift", WindowsShortcutFormatter.modifierKeyLabel(KeyEvent.VK_SHIFT, labels))
    assertNull(WindowsShortcutFormatter.modifierKeyLabel(KeyEvent.VK_A, labels))
  }
}
