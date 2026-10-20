package io.github.adrian2414745.winkeylabels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import javax.swing.JLabel

/**
 * The mapping fields always show the labels that apply for the current page state: detected labels in auto mode,
 * editable manual labels in manual mode, macOS symbols when the plugin is off.
 */
class WinSymbolsConfigurable : BoundConfigurable("Windows Key Labels for Mac") {
  private val options get() = service<WinSymbolsSettings>().state

  private fun storedManual() = options.let { ModifierLabels(it.command, it.control, it.option, it.shift) }

  override fun createPanel(): DialogPanel {
    lateinit var enabled: JBCheckBox
    lateinit var auto: JBRadioButton
    lateinit var manual: JBRadioButton
    lateinit var detected: JLabel
    lateinit var detectLink: ActionLink
    lateinit var command: JBTextField
    lateinit var control: JBTextField
    lateinit var option: JBTextField
    lateinit var shift: JBTextField
    val presetLinks = mutableListOf<ActionLink>()

    // Manual labels being edited; the fields hold them only while manual mode is shown
    var manualDraft = storedManual()
    var fieldsHoldDraft = false

    fun fieldLabels() = ModifierLabels(command.text, control.text, option.text, shift.text)

    fun setFields(labels: ModifierLabels) {
      command.text = labels.command
      control.text = labels.control
      option.text = labels.option
      shift.text = labels.shift
    }

    fun currentDraft() = if (fieldsHoldDraft) fieldLabels() else manualDraft

    fun shownLabels(): ModifierLabels {
      if (!enabled.isSelected) return ModifierLabels.MAC_SYMBOLS
      return when (val detection = service<KeyboardDetector>().detection) {
        is Detection.Detected -> detection.labels
        Detection.MacKeyboardOnly -> ModifierLabels.MAC_SYMBOLS
        is Detection.Undecided, null -> manualDraft
      }
    }

    fun refresh() {
      val on = enabled.isSelected
      val editable = on && manual.isSelected
      if (fieldsHoldDraft && !editable) manualDraft = fieldLabels()
      when {
        editable && !fieldsHoldDraft -> setFields(manualDraft)
        !editable -> setFields(shownLabels())
      }
      fieldsHoldDraft = editable
      detected.text = detectionText()

      auto.isEnabled = on
      manual.isEnabled = on
      detected.isEnabled = on
      detectLink.isEnabled = on && auto.isSelected
      listOf(command, control, option, shift).forEach { it.isEnabled = editable }
      presetLinks.forEach { it.isEnabled = editable }
    }

    fun detectAgain() {
      val job = service<KeyboardDetector>().refresh(force = true) ?: return
      detected.text = "Detecting…"
      job.invokeOnCompletion {
        ApplicationManager.getApplication().invokeLater({ refresh() }, ModalityState.any())
      }
    }

    val panel = panel {
      row {
        label(statusText())
      }
      row {
        enabled = checkBox("Show Windows modifier names instead of ⌘ ⌃ ⌥ ⇧")
          .bindSelected(options::enabled)
          .component
      }
      // bind(): member overload, the inline extensions are compiled for a newer JVM target than this plugin
      buttonsGroup {
        row {
          auto = radioButton("Detect from the connected keyboard", true).component
        }
        indent {
          row {
            detected = label(detectionText()).component
            detectLink = link("Detect again") { detectAgain() }.component
          }
        }
        row {
          manual = radioButton("Manual", false).component
        }
      }.bind(options::autoDetect.toMutableProperty(), Boolean::class.javaObjectType)
      group("Current Mapping") {
        row("⌘ Command:") { command = labelField().component }
        row("⌃ Control:") { control = labelField().component }
        row("⌥ Option:") { option = labelField().component }
        row("⇧ Shift:") { shift = labelField().component }
        row {
          presetLinks += link("Ctrl/Win swapped in macOS (⌘ = Ctrl)") { setFields(ModifierLabels.SWAPPED) }.component
          presetLinks += link("No remapping (⌘ = Win)") { setFields(ModifierLabels.STANDARD) }.component
        }
      }
      row {
        comment(
          "Applies to tooltips, popup menus, Find Action and the Keymap page. " +
          "The macOS menu bar is drawn by macOS and keeps its symbols. " +
          "Detection reads System Settings | Keyboard | Keyboard Shortcuts | Modifier Keys; " +
          "Karabiner and other remapping tools are not seen."
        )
      }

      // The manual mapping is saved whatever the mode; it is also the fallback when detection cannot decide
      onIsModified { currentDraft() != storedManual() }
      onApply {
        val labels = currentDraft()
        options.command = labels.command
        options.control = labels.control
        options.option = labels.option
        options.shift = labels.shift
      }
      onReset {
        manualDraft = storedManual()
        fieldsHoldDraft = false
        refresh()
      }
    }

    enabled.addActionListener {
      if (enabled.isSelected) {
        auto.isSelected = true
        detectAgain()
      }
      refresh()
    }
    auto.addActionListener { refresh() }
    manual.addActionListener { refresh() }

    refresh()
    detectAgain()
    return panel
  }

  private fun Row.labelField() =
    textField()
      .columns(10)
      .validationOnApply { if (it.isEnabled && it.text.isBlank()) error("Label cannot be empty") else null }

  private fun statusText(): String = when {
    !SystemInfo.isMac -> "Inactive: only needed on macOS."
    KeymapTextContextInstaller.current() is WindowsKeymapTextContext -> "Active."
    else -> "Inactive: restart the IDE. If it stays inactive, check idea.log."
  }

  private fun detectionText(): String = when (val detection = service<KeyboardDetector>().detection) {
    null -> "Not detected yet."
    is Detection.Detected -> "Detected: ${detection.keyboards.joinToString()}"
    Detection.MacKeyboardOnly -> "Only Apple keyboards connected: macOS symbols are shown."
    is Detection.Undecided -> "${detection.reason}. Using the manual mapping."
  }
}
