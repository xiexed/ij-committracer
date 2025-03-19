plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
    id("org.jetbrains.compose") version "1.5.11"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    google()
    maven("https://packages.jetbrains.team/maven/p/mpp/mpp")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2024.2.5")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add Git4Idea plugin dependency
        bundledPlugin("Git4Idea")
    }

    // Add JSON dependency for YouTrack API
    implementation("org.json:json:20240205")
    
    // Add OkHttp dependency for HiBob API
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    
    // Add Kotlin coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    
    // Add Compose dependencies
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    
    // Add JetBrains Compose integration with experimental components
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.desktop.components.splitPane)
    
    // Use compose-material3 from compose-jb
    implementation(compose.material3)
    
    // Add a serialization for potential future extensions
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Add Mockito for testing
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "251.*"
        }

        changeNotes = """
      Initial version with repository commit listing and YouTrack integration.
      - Added action to list all commits in the current repository
      - Added Git integration for commit history retrieval
      - Added YouTrack integration to show issue details for issues mentioned in commits
    """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
