package com.adriansalajan.winsymbols

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo

/** Installs [WindowsKeymapTextContext] before the first IDE frame is shown. */
class WinSymbolsStartup : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: List<String>) {
    if (!SystemInfo.isMac) return
    service<WinSymbolsSettings>()
    KeymapTextContextInstaller.install(WindowsKeymapTextContext(WinSymbolsSettings::activeLabels))
  }
}
