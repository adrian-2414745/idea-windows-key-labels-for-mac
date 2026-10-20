package com.adriansalajan.winsymbols

import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.KeyStroke

/** Swaps the real [KeymapUtil] context, so it also checks the installer against the platform version under test. */
class WindowsKeymapTextContextTest : BasePlatformTestCase() {
  private var labels: ModifierLabels? = ModifierLabels.SWAPPED
  private var previous: KeymapTextContext? = null

  override fun setUp() {
    super.setUp()
    previous = KeymapTextContextInstaller.install(WindowsKeymapTextContext { labels })
    assertNotNull("installer failed, see log", previous)
  }

  override fun tearDown() {
    try {
      previous?.let { KeymapTextContextInstaller.install(it) }
    }
    finally {
      super.tearDown()
    }
  }

  fun testKeymapUtilUsesInstalledContext() {
    assertInstanceOf(KeymapTextContextInstaller.current(), WindowsKeymapTextContext::class.java)
    assertEquals("Ctrl+Shift+N", KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("meta shift N")))
  }

  fun testTwoStrokeShortcut() {
    val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke("meta K"), KeyStroke.getKeyStroke("control V"))
    assertEquals("Ctrl+K, Win+V", KeymapUtil.getShortcutText(shortcut))
  }

  fun testMouseShortcut() {
    val shortcut = MouseShortcut(MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK or InputEvent.ALT_DOWN_MASK, 1)
    assertEquals("Ctrl+Alt+Click", KeymapUtil.getShortcutText(shortcut))
  }

  fun testDoubleModifierGesture() {
    val stroke = KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK)
    val shortcut = KeyboardModifierGestureShortcut.newInstance(KeyboardGestureAction.ModifierType.dblClick, stroke)
    assertEquals("Shift Shift", KeymapUtil.getShortcutText(shortcut))
  }

  fun testDisabledFallsBackToPlatformText() {
    labels = null
    val stroke = KeyStroke.getKeyStroke("meta shift N")
    assertEquals(KeymapTextContext().getKeystrokeText(stroke), KeymapUtil.getKeystrokeText(stroke))
  }
}
