package io.github.adrian2414745.winkeylabels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are real `hidutil list` / `defaults -currentHost export -g -` output from a MacBook with a remapped
 * Logitech keyboard and a Logitech mouse that also registers as a keyboard.
 */
class KeyboardDetectionTest {
  private fun fixture(name: String): String =
    javaClass.getResource("/fixtures/$name")!!.readText()

  private val keyboards = HidutilParser.parse(fixture("hidutil-list.txt"))
  private val mice = HidutilParser.parse(fixture("hidutil-list-mice.txt"))
  private val mappings = ModifierMappingParser.parse(fixture("defaults-export.plist"))

  private fun keyboard(product: String, vendorId: Int = 0x46d, productId: Int = 0x1, builtIn: Boolean = false) =
    HidDevice(vendorId, productId, product, builtIn, "USB")

  // HID usages, page 7
  private val lCtrl = 0x7000000E0L
  private val lGui = 0x7000000E3L
  private val rCtrl = 0x7000000E4L
  private val rGui = 0x7000000E7L
  private val capsLock = 0x700000039L

  @Test
  fun `hidutil output lists each keyboard once`() {
    assertEquals(
      listOf(
        HidDevice(0x46d, 0xc339, "Pro Gaming Keyboard", builtIn = false, transport = "USB"),
        HidDevice(0x5ac, 0x340, "Apple Internal Keyboard / Trackpad", builtIn = true, transport = "USB"),
        HidDevice(0x5ac, 0x8600, "TouchBarUserDevice", builtIn = true, transport = "Virtual"),
        HidDevice(0x46d, 0xc07d, "Gaming Mouse G502", builtIn = false, transport = "USB"),
      ).sortedBy { it.product },
      keyboards.sortedBy { it.product },
    )
  }

  @Test
  fun `mappings are keyed by vendor and product and other keys are skipped`() {
    assertEquals(setOf("10730-258", "1133-49277", "1133-49977", "1452-832", "5426-132", "9494-32"), mappings.keys)
    assertEquals(6, mappings.getValue("1133-49977").size)
  }

  @Test
  fun `remapped keyboard wins over a mouse without remapping`() {
    val detection = KeyboardMappingResolver.resolve(keyboards, mice, mappings)
    assertEquals(Detection.Detected(ModifierLabels.SWAPPED, listOf("Pro Gaming Keyboard")), detection)
  }

  @Test
  fun `unplugging the keyboard leaves the mouse and gives macOS symbols`() {
    val withoutKeyboard = keyboards.filter { it.product != "Pro Gaming Keyboard" }
    assertTrue(withoutKeyboard.any { it.product == "Gaming Mouse G502" })
    assertEquals(Detection.MacKeyboardOnly, KeyboardMappingResolver.resolve(withoutKeyboard, mice, mappings))
  }

  @Test
  fun `remapped mouse still counts as a keyboard`() {
    val mouse = keyboard("Remapping Mouse", productId = 7)
    val swap = listOf(ModifierRemap(lCtrl, lGui), ModifierRemap(lGui, lCtrl), ModifierRemap(rCtrl, rGui), ModifierRemap(rGui, rCtrl))
    val remap = mapOf("1133-7" to swap)
    val detection = KeyboardMappingResolver.resolve(listOf(mouse), listOf(mouse), remap)
    assertEquals(Detection.Detected(ModifierLabels.SWAPPED, listOf("Remapping Mouse")), detection)
  }

  @Test
  fun `usages stored as real numbers are read`() {
    val xml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <plist version="1.0"><dict>
        <key>com.apple.keyboard.modifiermapping.1-2-0</key>
        <array>
          <dict>
            <key>HIDKeyboardModifierMappingDst</key><real>30064771299</real>
            <key>HIDKeyboardModifierMappingSrc</key><real>3.0064771296E10</real>
          </dict>
          <dict>
            <key>HIDKeyboardModifierMappingDst</key><integer>30064771296</integer>
            <key>HIDKeyboardModifierMappingSrc</key><real>30064771299.0</real>
          </dict>
        </array>
      </dict></plist>
    """.trimIndent()
    assertEquals(
      mapOf("1-2" to listOf(ModifierRemap(lCtrl, lGui), ModifierRemap(lGui, lCtrl))),
      ModifierMappingParser.parse(xml),
    )
  }

  @Test
  fun `identity mapping gives standard labels`() {
    assertEquals(ModifierLabels.STANDARD, KeyboardMappingResolver.labelsFor(mappings.getValue("1133-49277")))
    assertEquals(ModifierLabels.STANDARD, KeyboardMappingResolver.labelsFor(emptyList()))
  }

  @Test
  fun `crossed left and right keys still give Ctrl and Win`() {
    // 9494-32 maps RCtrl to LCmd, LCtrl to RCmd, RWin to RCtrl, LWin to LCtrl
    assertEquals(ModifierLabels.SWAPPED, KeyboardMappingResolver.labelsFor(mappings.getValue("9494-32")))
  }

  @Test
  fun `several keys producing one modifier are joined`() {
    val labels = KeyboardMappingResolver.labelsFor(listOf(ModifierRemap(capsLock, lCtrl)))
    assertEquals("Ctrl/Caps Lock", labels.control)
  }

  @Test
  fun `modifier no key produces keeps its macOS symbol`() {
    val labels = KeyboardMappingResolver.labelsFor(listOf(ModifierRemap(lGui, lCtrl), ModifierRemap(rGui, rCtrl)))
    assertEquals("⌘", labels.command)
    assertEquals("Ctrl/Win", labels.control)
  }

  @Test
  fun `only Apple keyboards means macOS symbols`() {
    val apple = keyboards.filter { it.vendorId == 0x5ac } + keyboard("Magic Keyboard", vendorId = 0x5ac)
    assertEquals(Detection.MacKeyboardOnly, KeyboardMappingResolver.resolve(apple, mice, mappings))
  }

  @Test
  fun `external keyboard without mapping gives standard labels`() {
    val detection = KeyboardMappingResolver.resolve(listOf(keyboard("Plain Keyboard")), mice, mappings)
    assertEquals(Detection.Detected(ModifierLabels.STANDARD, listOf("Plain Keyboard")), detection)
  }

  @Test
  fun `keyboards remapped differently cannot be decided`() {
    val first = keyboard("First", productId = 1)
    val second = keyboard("Second", productId = 2)
    val custom = mapOf(
      "1133-1" to listOf(ModifierRemap(lCtrl, lGui), ModifierRemap(lGui, lCtrl)),
      "1133-2" to listOf(ModifierRemap(capsLock, lCtrl)),
    )
    val detection = KeyboardMappingResolver.resolve(listOf(first, second), emptyList(), custom)
    assertTrue(detection.toString(), detection is Detection.Undecided)
    assertEquals("Connected keyboards are remapped differently: First, Second", (detection as Detection.Undecided).reason)
  }

  @Test(expected = IllegalStateException::class)
  fun `unknown hidutil header is rejected`() {
    HidutilParser.parse("VendorID Something\n0x1 0x2\n")
  }
}
