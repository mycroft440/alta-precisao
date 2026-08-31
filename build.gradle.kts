buildscript {
    dependencies {
        // AGP 9 ships with built-in Kotlin. We explicitly raise KGP so the
        // Compose compiler plugin and Kotlin compiler stay on the same version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
