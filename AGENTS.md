# AGENTS.md

- [README.md](README.md): what the plugin does, for users.
- [agent-docs/architecture.md](agent-docs/architecture.md): folder structure, how it works, reasons for technical decisions.
- [agent-docs/lessons-learned.md](agent-docs/lessons-learned.md): read before changing code; append when you fix a
  non-obvious problem.

## Build and test

```sh
./gradlew build buildPlugin -PplatformLocalPath="$HOME/Applications/IntelliJ IDEA.app"
```

Without `platformLocalPath` the IDE version from `gradle.properties` is downloaded.

## Rules

- Keep test fixtures real command output.
- Update `agent-docs/architecture.md` when a design decision changes.
