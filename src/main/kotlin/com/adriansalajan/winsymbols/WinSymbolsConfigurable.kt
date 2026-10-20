package com.adriansalajan.winsymbols

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

class WinSymbolsConfigurable : BoundConfigurable("Windows Shortcut Symbols") {
  private val options get() = service<WinSymbolsSettings>().state

  override fun createPanel(): DialogPanel {
    lateinit var command: JBTextField
    lateinit var control: JBTextField
    lateinit var option: JBTextField
    lateinit var shift: JBTextField

    fun applyPreset(labels: ModifierLabels) {
      command.text = labels.command
      control.text = labels.control
      option.text = labels.option
      shift.text = labels.shift
    }

    return panel {
      row {
        label(statusText())
      }
      row {
        checkBox("Show Windows modifier names instead of ⌘ ⌃ ⌥ ⇧").bindSelected(options::enabled)
      }
      group("Labels") {
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
          "The macOS menu bar is drawn by macOS and keeps its symbols."
        )
      }
    }
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
}
