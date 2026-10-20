package io.github.adrian2414745.winkeylabels

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** A connected HID device, as listed by `hidutil list`. */
data class HidDevice(
  val vendorId: Int,
  val productId: Int,
  val product: String,
  val builtIn: Boolean,
  val transport: String,
)

/** One entry of a macOS modifier mapping: physical key [src] acts as [dst]. Values are HID usages (page shl 32 or usage). */
data class ModifierRemap(val src: Long, val dst: Long)

sealed interface Detection {
  /** External keyboard(s) found; [labels] name the physical keys that produce each macOS modifier. */
  data class Detected(val labels: ModifierLabels, val keyboards: List<String>) : Detection

  /** Only Apple keyboards (or mice with keyboard interfaces) are connected: the macOS symbols match the key caps. */
  data object MacKeyboardOnly : Detection

  /** Detection could not decide; manual labels are used. */
  data class Undecided(val reason: String) : Detection
}

object KeyboardMappingResolver {
  private const val APPLE_VENDOR_ID = 0x5AC

  private const val L_CTRL = 0x7000000E0L
  private const val L_SHIFT = 0x7000000E1L
  private const val L_ALT = 0x7000000E2L
  private const val L_GUI = 0x7000000E3L
  private const val R_CTRL = 0x7000000E4L
  private const val R_SHIFT = 0x7000000E5L
  private const val R_ALT = 0x7000000E6L
  private const val R_GUI = 0x7000000E7L
  private const val CAPS_LOCK = 0x700000039L
  private const val FN_APPLE = 0xFF00000003L
  private const val FN_KEYBOARD = 0xFF0100000003L

  // Left keys first, so their names come first when several keys produce one modifier
  private val PHYSICAL_KEYS = linkedMapOf(
    L_CTRL to "Ctrl", L_SHIFT to "Shift", L_ALT to "Alt", L_GUI to "Win",
    R_CTRL to "Ctrl", R_SHIFT to "Shift", R_ALT to "Alt", R_GUI to "Win",
    CAPS_LOCK to "Caps Lock", FN_APPLE to "Fn", FN_KEYBOARD to "Fn",
  )

  private enum class MacModifier(val symbol: String) { COMMAND("⌘"), CONTROL("⌃"), OPTION("⌥"), SHIFT("⇧") }

  private fun macModifier(usage: Long): MacModifier? = when (usage) {
    L_GUI, R_GUI -> MacModifier.COMMAND
    L_CTRL, R_CTRL -> MacModifier.CONTROL
    L_ALT, R_ALT -> MacModifier.OPTION
    L_SHIFT, R_SHIFT -> MacModifier.SHIFT
    else -> null
  }

  /** Mapping keys look like `com.apple.keyboard.modifiermapping.<vendorId>-<productId>-0`. */
  fun mappingKey(vendorId: Int, productId: Int) = "$vendorId-$productId"

  /** Labels for a Windows keyboard with [remaps] applied; an empty list means no remapping. */
  fun labelsFor(remaps: List<ModifierRemap>): ModifierLabels {
    val dst = remaps.associate { it.src to it.dst }
    fun label(modifier: MacModifier): String =
      PHYSICAL_KEYS.filterKeys { macModifier(dst[it] ?: it) == modifier }.values.distinct()
        .joinToString("/")
        .ifEmpty { modifier.symbol }
    return ModifierLabels(
      command = label(MacModifier.COMMAND),
      control = label(MacModifier.CONTROL),
      option = label(MacModifier.OPTION),
      shift = label(MacModifier.SHIFT),
    )
  }

  /**
   * Built-in, virtual and Apple keyboards are ignored. Keyboards without remapping only count when no connected
   * keyboard is remapped, and then not if they are also in [pointingDevices]: mice with macro keys register as keyboards.
   */
  fun resolve(
    keyboards: List<HidDevice>,
    pointingDevices: List<HidDevice>,
    mappings: Map<String, List<ModifierRemap>>,
  ): Detection {
    val external = keyboards
      .filter { !it.builtIn && it.transport != "Virtual" && it.vendorId != APPLE_VENDOR_ID }
      .distinctBy { it.vendorId to it.productId }
    val pointers = pointingDevices.map { it.vendorId to it.productId }.toSet()

    val labeled = external.map { it to labelsFor(mappings[mappingKey(it.vendorId, it.productId)].orEmpty()) }
    val remapped = labeled.filter { it.second != ModifierLabels.STANDARD }
    val chosen = remapped.ifEmpty { labeled.filter { (it.first.vendorId to it.first.productId) !in pointers } }
    if (chosen.isEmpty()) return Detection.MacKeyboardOnly
    val distinctLabels = chosen.map { it.second }.distinct()
    if (distinctLabels.size > 1) {
      return Detection.Undecided("Connected keyboards are remapped differently: " + chosen.joinToString { it.first.product })
    }
    return Detection.Detected(distinctLabels.single(), chosen.map { it.first.product })
  }
}

object HidutilParser {
  private val COLUMNS = listOf("VendorID", "ProductID", "Transport", "Product", "Built-In")

  /** Parses the fixed-width tables of `hidutil list`; columns are located by their header. */
  fun parse(output: String): List<HidDevice> {
    val result = mutableListOf<HidDevice>()
    var columns: List<Pair<String, Int>>? = null
    for (line in output.lines()) {
      if (line.startsWith("VendorID")) {
        columns = Regex("\\S+").findAll(line).map { it.value to it.range.first }.toList()
        check(columns.map { it.first }.containsAll(COLUMNS)) { "Unexpected hidutil header: $line" }
        continue
      }
      val header = columns ?: continue
      if (line.isBlank()) {
        columns = null
        continue
      }
      val values = header.mapIndexed { i, (name, start) ->
        val end = header.getOrNull(i + 1)?.second ?: line.length
        name to line.substring(minOf(start, line.length), minOf(end, line.length)).trim()
      }.toMap()
      result += HidDevice(
        vendorId = Integer.decode(values.getValue("VendorID")),
        productId = Integer.decode(values.getValue("ProductID")),
        product = values.getValue("Product"),
        builtIn = values.getValue("Built-In") == "1",
        transport = values.getValue("Transport"),
      )
    }
    return result.distinct()
  }
}

object ModifierMappingParser {
  private const val PREFIX = "com.apple.keyboard.modifiermapping."

  /** Parses `defaults -currentHost export -g -` into mappings keyed by [KeyboardMappingResolver.mappingKey]. */
  fun parse(plistXml: String): Map<String, List<ModifierRemap>> {
    val factory = DocumentBuilderFactory.newInstance().apply {
      setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }
    val root = factory.newDocumentBuilder().parse(InputSource(StringReader(plistXml))).documentElement
    val topDict = root.childElements().single { it.tagName == "dict" }

    val result = mutableMapOf<String, List<ModifierRemap>>()
    for ((key, value) in topDict.dictEntries()) {
      if (!key.startsWith(PREFIX) || value.tagName != "array") continue
      // "<vendorId>-<productId>-<n>"; the last part is ignored
      val parts = key.removePrefix(PREFIX).split('-')
      if (parts.size < 2) continue
      val remaps = value.childElements().filter { it.tagName == "dict" }.mapNotNull { entry ->
        val fields = entry.dictEntries().toMap()
        val src = fields["HIDKeyboardModifierMappingSrc"]?.usage()
        val dst = fields["HIDKeyboardModifierMappingDst"]?.usage()
        if (src != null && dst != null) ModifierRemap(src, dst) else null
      }
      result.putIfAbsent("${parts[0]}-${parts[1]}", remaps)
    }
    return result
  }

  /** Usages are stored as `<integer>` or `<real>` (e.g. `30064771299` or `30064771299.0`). */
  private fun Element.usage(): Long? =
    textContent.trim().toBigDecimalOrNull()?.let { runCatching { it.longValueExact() }.getOrNull() }

  private fun Element.childElements(): List<Element> =
    (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

  private fun Element.dictEntries(): List<Pair<String, Element>> =
    childElements().chunked(2).filter { it.size == 2 && it[0].tagName == "key" }.map { it[0].textContent to it[1] }
}
