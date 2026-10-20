package com.adriansalajan.winsymbols

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import javax.swing.JLabel

class WinSymbolsConfigurable : BoundConfigurable("Windows Shortcut Symbols") {
  private val options get() = service<WinSymbolsSettings>().state

  override fun createPanel(): DialogPanel {
    lateinit var command: JBTextField
    lateinit var control: JBTextField
    lateinit var option: JBTextField
    lateinit var shift: JBTextField
    lateinit var detected: JLabel

    fun applyPreset(labels: ModifierLabels) {
      command.text = labels.command
      control.text = labels.control
      option.text = labels.option
      shift.text = labels.shift
    }

    fun detectAgain() {
      detected.text = "Detecting…"
      val job = service<KeyboardDetector>().refresh(force = true) ?: return
      job.invokeOnCompletion {
        ApplicationManager.getApplication().invokeLater({ detected.text = detectionText() }, ModalityState.any())
      }
    }

    val panel = panel {
      row {
        label(statusText())
      }
      row {
        checkBox("Show Windows modifier names instead of ⌘ ⌃ ⌥ ⇧").bindSelected(options::enabled)
      }
      // bind(): member overload, the inline extensions are compiled for a newer JVM target than this plugin
      buttonsGroup {
        row {
          radioButton("Detect from the connected keyboard", true)
        }
        indent {
          row {
            detected = label(detectionText()).component
            link("Detect again") { detectAgain() }
          }
        }
        row {
          radioButton("Always use the manual labels", false)
        }
      }.bind(options::autoDetect.toMutableProperty(), Boolean::class.javaObjectType)
      group("Manual Labels (Also Used When Detection Cannot Decide)") {
        row("⌘ Command:") { command = labelField().bindText(options::command).component }
        row("⌃ Control:") { control = labelField().bindText(options::control).component }
        row("⌥ Option:") { option = labelField().bindText(options::option).component }
        row("⇧ Shift:") { shift = labelField().bindText(options::shift).component }
        row {
          link("Ctrl/Win swapped in macOS (⌘ = Ctrl)") { applyPreset(ModifierLabels.SWAPPED) }
          link("No remapping (⌘ = Win)") { applyPreset(ModifierLabels.STANDARD) }
        }
      }
      row {
        comment(
          "Applies to tooltips, popup menus, Find Action and the Keymap page. " +
          "The macOS menu bar is drawn by macOS and keeps its symbols. " +
          "Detection reads System Settings | Keyboard | Modifier Keys; Karabiner and other remapping tools are not seen."
        )
      }
    }
    detectAgain()
    return panel
  }

  private fun Row.labelField() =
    textField()
      .columns(10)
      .validationOnApply { if (it.text.isBlank()) error("Label cannot be empty") else null }

  private fun statusText(): String = when {
    !SystemInfo.isMac -> "Inactive: only needed on macOS."
    KeymapTextContextInstaller.current() is WindowsKeymapTextContext -> "Active."
    else -> "Inactive: restart the IDE. If it stays inactive, check idea.log."
  }

  private fun detectionText(): String = when (val detection = service<KeyboardDetector>().detection) {
    null -> "Not detected yet."
    is Detection.Detected -> {
      val l = detection.labels
      "${detection.keyboards.joinToString()}: ⌘ = ${l.command}, ⌃ = ${l.control}, ⌥ = ${l.option}, ⇧ = ${l.shift}"
    }
    Detection.MacKeyboardOnly -> "Only Apple keyboards connected: macOS symbols are shown."
    is Detection.Undecided -> "${detection.reason}. Using the manual labels."
  }
}
