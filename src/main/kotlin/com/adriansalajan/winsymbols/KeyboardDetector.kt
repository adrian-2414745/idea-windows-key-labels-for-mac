package com.adriansalajan.winsymbols

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads connected keyboards and mice (`hidutil`) and the keyboards' macOS modifier remapping (`defaults`). */
@Service(Service.Level.APP)
class KeyboardDetector(private val scope: CoroutineScope) {
  private val mutex = Mutex()

  @Volatile
  private var lastStart = 0L

  /** Null until the first detection finishes. */
  @Volatile
  var detection: Detection? = null
    private set

  /** Starts a detection in the background, unless one started less than [THROTTLE_MS] ago and [force] is false. */
  fun refresh(force: Boolean = false): Job? {
    val now = System.currentTimeMillis()
    if (!force && now - lastStart < THROTTLE_MS) return null
    lastStart = now
    return scope.launch(Dispatchers.IO) {
      mutex.withLock { detection = detect() }
    }
  }

  private fun detect(): Detection = try {
    val keyboards = HidutilParser.parse(run("hidutil", "list", "--matching", """{"DeviceUsagePage":1,"DeviceUsage":6}"""))
    val mice = HidutilParser.parse(run("hidutil", "list", "--matching", """{"DeviceUsagePage":1,"DeviceUsage":2}"""))
    val mappings = ModifierMappingParser.parse(run("defaults", "-currentHost", "export", "-g", "-"))
    KeyboardMappingResolver.resolve(keyboards, mice, mappings)
  }
  catch (e: Exception) {
    LOG.warn("Keyboard detection failed", e)
    Detection.Undecided("Detection failed: ${e.message}")
  }

  private fun run(vararg command: String): String {
    val output = ExecUtil.execAndGetOutput(GeneralCommandLine(*command), TIMEOUT_MS)
    check(!output.isTimeout && output.exitCode == 0) {
      "`${command.first()}` failed (exit ${output.exitCode}): ${output.stderr.trim()}"
    }
    return output.stdout
  }

  private companion object {
    val LOG = logger<KeyboardDetector>()
    const val THROTTLE_MS = 5_000L
    const val TIMEOUT_MS = 5_000
  }
}
