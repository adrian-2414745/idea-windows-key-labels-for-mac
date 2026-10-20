KeymapUtil's KeymapTextContext field is named ourDefaultKeymapTextContext in 2025.2 and defaultKeymapTextContext in 2026.x -> find the private static final field by type, write it with Unsafe (Field.set fails on static final)
Gradle IntelliJ Platform plugin 2.x 'build' task does not produce the plugin zip -> run buildPlugin; output in build/distributions/
macOS modifiermapping plist stores HID usages as <integer> or <real> -> parse via BigDecimal.longValueExact, not toLong
Kotlin UI DSL inline extensions (e.g. ButtonsGroup.bind(KMutableProperty0)) in IDE 2026.2 are JVM 25 bytecode, fail to inline into jvmTarget 21 -> use the non-inline member overload (bind(MutableProperty, Class))
Gaming mice register a HID keyboard interface (usage 1/6) -> exclude devices also listed as pointers (usage 1/2) unless remapped
zsh does not word-split unquoted $VAR in for loops (whole list becomes one arg) -> pipe the list into `while IFS= read -r f`
