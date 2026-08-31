import org.gradle.authentication.http.BasicAuthentication

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            val downloadToken = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull
                ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")
            if (!downloadToken.isNullOrBlank()) {
                credentials {
                    username = "mapbox"
                    password = downloadToken
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }
}

rootProject.name = "GeoMeasure"
include(":app")
