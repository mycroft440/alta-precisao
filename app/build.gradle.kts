import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

val mapboxPublicToken = providers.gradleProperty("MAPBOX_ACCESS_TOKEN")
    .orElse(providers.environmentVariable("MAPBOX_ACCESS_TOKEN"))
    .orNull
    ?: localProperties.getProperty("MAPBOX_ACCESS_TOKEN").orEmpty()

android {
    namespace = "com.geomeasure.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.geomeasure.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "0.2.2"
        resValue("string", "mapbox_access_token", mapboxPublicToken)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("net.sf.geographiclib:GeographicLib-Java:2.1")
    implementation("com.mapbox.maps:android-ndk27:11.28.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
