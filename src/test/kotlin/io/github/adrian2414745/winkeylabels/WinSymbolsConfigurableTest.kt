package io.github.adrian2414745.winkeylabels

import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

class WinSymbolsConfigurableTest : BasePlatformTestCase() {
  private lateinit var configurable: WinSymbolsConfigurable
  private lateinit var panel: JComponent
  private val detector get() = service<KeyboardDetector>()
  private val settings get() = service<WinSymbolsSettings>()

  private val detected = Detection.Detected(ModifierLabels.SWAPPED, listOf("Test Keyboard"))

  override fun setUp() {
    super.setUp()
    detector.source = { detected }
    settings.loadState(WinSymbolsSettings.Options().apply {
      command = "Win"
      control = "Ctrl"
      option = "Alt"
      shift = "Shift"
    })
    configurable = WinSymbolsConfigurable()
    panel = configurable.createComponent()!!
    configurable.reset()
    awaitDetection(detected)
  }

  override fun tearDown() {
    try {
      configurable.disposeUIResources()
      settings.loadState(WinSymbolsSettings.Options())
    }
    finally {
      super.tearDown()
    }
  }

  /** The detector is an app service, so a previous test's result may still be there until the new one lands. */
  private fun awaitDetection(expected: Detection) {
    PlatformTestUtil.waitWithEventsDispatching("detection did not finish", { detector.detection == expected }, 10)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun checkBox() = UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).single()
  private fun radio(text: String) = UIUtil.findComponentsOfType(panel, JBRadioButton::class.java).single { it.text == text }
  private fun auto() = radio("Detect from the connected keyboard")
  private fun manual() = radio("Manual")
  private fun fields() = UIUtil.findComponentsOfType(panel, JBTextField::class.java)
  private fun fieldTexts() = fields().map { it.text }
  private fun presets() = UIUtil.findComponentsOfType(panel, ActionLink::class.java).filter { it.text.contains("⌘ =") }

  fun testAutoShowsDetectedMappingReadOnly() {
    assertTrue(auto().isSelected)
    assertEquals(listOf("Ctrl", "Win", "Alt", "Shift"), fieldTexts())
    assertTrue(fields().none { it.isEnabled })
    assertTrue(presets().none { it.isEnabled })
    assertFalse(configurable.isModified)
  }

  fun testManualShowsStoredMappingEditable() {
    manual().doClick()
    assertEquals(listOf("Win", "Ctrl", "Alt", "Shift"), fieldTexts())
    assertTrue(fields().all { it.isEnabled })
    assertTrue(presets().all { it.isEnabled })
  }

  fun testManualEditsSurviveSwitchingToAutoAndBack() {
    manual().doClick()
    fields()[0].text = "Super"
    auto().doClick()
    assertEquals("Ctrl", fields()[0].text)
    manual().doClick()
    assertEquals("Super", fields()[0].text)

    assertTrue(configurable.isModified)
    configurable.apply()
    assertEquals("Super", settings.state.command)
    assertFalse(settings.state.autoDetect)
  }

  fun testUncheckingDisablesEverythingAndShowsMacSymbols() {
    checkBox().doClick()
    assertFalse(checkBox().isSelected)
    assertEquals(listOf("⌘", "⌃", "⌥", "⇧"), fieldTexts())
    assertFalse(auto().isEnabled)
    assertFalse(manual().isEnabled)
    assertTrue(fields().none { it.isEnabled })
  }

  fun testCheckingSelectsAutoAndDetects() {
    manual().doClick()
    checkBox().doClick()
    detector.source = { Detection.Detected(ModifierLabels.STANDARD, listOf("Other Keyboard")) }
    checkBox().doClick()
    assertTrue(auto().isSelected)
    awaitDetection(Detection.Detected(ModifierLabels.STANDARD, listOf("Other Keyboard")))
    assertEquals(listOf("Win", "Ctrl", "Alt", "Shift"), fieldTexts())
    assertTrue(fields().none { it.isEnabled })
  }

  fun testOnlyAppleKeyboardsShowsMacSymbols() {
    detector.source = { Detection.MacKeyboardOnly }
    UIUtil.findComponentsOfType(panel, ActionLink::class.java).single { it.text == "Detect again" }.doClick()
    awaitDetection(Detection.MacKeyboardOnly)
    assertEquals(listOf("⌘", "⌃", "⌥", "⇧"), fieldTexts())
  }
}
