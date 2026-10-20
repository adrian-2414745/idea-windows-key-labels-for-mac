package io.github.adrian2414745.winkeylabels

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFrame

/** Installs [WindowsKeymapTextContext] before the first IDE frame is shown. */
class WinSymbolsStartup : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: List<String>) {
    if (!SystemInfo.isMac) return
    service<WinSymbolsSettings>()
    KeymapTextContextInstaller.install(WindowsKeymapTextContext(WinSymbolsSettings::activeLabels))
    service<KeyboardDetector>().refresh(force = true)
  }
}

/** Re-detects the keyboard when the IDE gets focus, to catch keyboards plugged in meanwhile. */
class WinSymbolsActivation : ApplicationActivationListener {
  override fun applicationActivated(ideFrame: IdeFrame) {
    val options = service<WinSymbolsSettings>().state
    if (!SystemInfo.isMac || !options.enabled || !options.autoDetect) return
    service<KeyboardDetector>().refresh()
  }
}
