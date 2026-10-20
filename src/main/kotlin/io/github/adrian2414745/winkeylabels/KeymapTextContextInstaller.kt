package io.github.adrian2414745.winkeylabels

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapTextContext
import com.intellij.openapi.keymap.KeymapUtil
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Swaps the [KeymapTextContext] that [KeymapUtil] uses for all shortcut text.
 *
 * The platform has no extension point for this: the context is a `private static final` field
 * (`defaultKeymapTextContext` in 2026.x, `ourDefaultKeymapTextContext` in 2025.x), so it is found by type
 * and written with [Unsafe]. Install it early: JIT-compiled code may keep the old instance.
 */
object KeymapTextContextInstaller {
  private val LOG = logger<KeymapTextContextInstaller>()

  /** Returns the replaced context, or null if the replacement failed. */
  @Synchronized
  fun install(context: KeymapTextContext): KeymapTextContext? {
    return try {
      val field = contextField()
      val previous = field.get(null) as KeymapTextContext
      write(field, context)
      previous
    }
    catch (e: Throwable) {
      LOG.warn("Cannot replace KeymapUtil text context; shortcuts keep macOS symbols", e)
      null
    }
  }

  fun current(): KeymapTextContext = contextField().get(null) as KeymapTextContext

  private fun contextField(): Field {
    val field = KeymapUtil::class.java.declaredFields.single {
      Modifier.isStatic(it.modifiers) && it.type == KeymapTextContext::class.java
    }
    field.isAccessible = true
    return field
  }

  @Suppress("DEPRECATION")
  private fun write(field: Field, value: KeymapTextContext) {
    val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let {
      it.isAccessible = true
      it.get(null) as Unsafe
    }
    unsafe.putObjectVolatile(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value)
  }
}
