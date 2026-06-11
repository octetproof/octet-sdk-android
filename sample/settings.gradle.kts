pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Consumes the published OctetSDK AAR from this very repository's
        // orphan `mvn-repo` branch.
        maven {
            url = uri("https://raw.githubusercontent.com/octetproof/octet-sdk-android/mvn-repo")
        }
    }
}

rootProject.name = "OctetV1Toy"
include(":app")
