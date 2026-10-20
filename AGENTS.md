# AGENTS.md

Read [README.md](README.md) first: what the plugin does, detection rules, how the `KeymapUtil` swap works, code map, build.

Also read [agent-docs/lessons-learned.md](agent-docs/lessons-learned.md) and append to it when you fix a non-obvious problem.

## Notes for agents

- Build and test: `./gradlew build buildPlugin -PplatformLocalPath="$HOME/Applications/IntelliJ IDEA.app"`
  (without `platformLocalPath` the IDE from `gradle.properties` is downloaded).
- Compatibility: `sinceBuild = 252`, `jvmTarget = 21`, Kotlin `apiVersion = 2.2`. Code is compiled against 2026.2, so check
  that platform APIs used also exist in 2025.2. Avoid platform `inline` Kotlin functions: 2026.2 ships them as JVM 25
  bytecode, which cannot be inlined into JVM 21 code.
- `KeymapTextContextInstaller` depends on platform internals (a private static field of `KeymapUtil`). If a platform
  update breaks it, `WindowsKeymapTextContextTest` fails.
- Shortcut text is computed on the EDT and often: `WindowsKeymapTextContext` and `WinSymbolsSettings.activeLabels()` must
  not block or run processes. Detection runs in `KeyboardDetector` on `Dispatchers.IO`.
- Test fixtures in `src/test/resources/fixtures/` are real command output; keep new fixtures real too.
