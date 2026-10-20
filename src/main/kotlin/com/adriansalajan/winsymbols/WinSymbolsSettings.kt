package com.adriansalajan.winsymbols

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.SystemInfo

@Service(Service.Level.APP)
@State(name = "WindowsShortcutSymbols", storages = [Storage("windowsShortcutSymbols.xml")])
class WinSymbolsSettings : PersistentStateComponent<WinSymbolsSettings.Options> {

  class Options {
    var enabled: Boolean = true
    /** Take labels from the connected keyboard; the manual labels are the fallback. */
    var autoDetect: Boolean = true
    var command: String = ModifierLabels.SWAPPED.command
    var control: String = ModifierLabels.SWAPPED.control
    var option: String = ModifierLabels.SWAPPED.option
    var shift: String = ModifierLabels.SWAPPED.shift
  }

  private var options = Options()

  override fun getState(): Options = options

  override fun loadState(state: Options) {
    options = state
  }

  /** Labels to show, or null when the platform text should be used. */
  fun activeLabels(): ModifierLabels? {
    val o = options
    if (!o.enabled) return null
    if (o.autoDetect) {
      when (val detection = ApplicationManager.getApplication().serviceIfCreated<KeyboardDetector>()?.detection) {
        is Detection.Detected -> return detection.labels
        Detection.MacKeyboardOnly -> return null
        is Detection.Undecided, null -> {}
      }
    }
    return ModifierLabels(command = o.command, control = o.control, option = o.option, shift = o.shift)
  }

  companion object {
    /** Null when not on macOS or before the settings service is created at startup. */
    fun activeLabels(): ModifierLabels? {
      if (!SystemInfo.isMac) return null
      return ApplicationManager.getApplication()?.serviceIfCreated<WinSymbolsSettings>()?.activeLabels()
    }
  }
}
