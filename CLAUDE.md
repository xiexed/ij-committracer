# CLAUDE.md - Assistant Configuration

## Commands to Run

### Development
- **Compile:** `./gradlew compileKotlin`
- **Run/Debug:** `./gradlew runIde`, don't use it
- **Build Plugin:** `./gradlew buildPlugin`
- **Clean:** `./gradlew clean`, don't use it

### Workflow Guidelines
- Commit after every meaningful change
- Don't mention Claude in commit messages
- Keep commit messages concise and descriptive
- Verify code compiles before committing: `./gradlew compileKotlin`

## IntelliJ Plugin Development Guidelines

### Architecture
- Follow IntelliJ Platform Architecture patterns
- Use Service, Extension, and Action patterns appropriately
- Prefer light services when possible for better performance

### Kotlin Style
- Use idiomatic Kotlin (extension functions, lambdas, etc.)
- Follow property delegation patterns where appropriate
- Use coroutines for asynchronous operations 
- Leverage data classes for models
- Use sealed classes for representing finite sets of types

### Plugin Configuration
- Register components in `plugin.xml`
- Use resource bundles for i18n with `*Bundle.properties` files
- Store settings in application or project level using PersistentStateComponent

### Performance Considerations
- Avoid UI freezes by using background tasks
- Use read/write actions appropriately when accessing PSI
- Leverage caching where appropriate
- Be mindful of memory leaks in listeners
- Follow Compose performance best practices for efficient recomposition