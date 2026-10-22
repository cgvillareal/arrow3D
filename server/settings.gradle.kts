// The ranking server is its own build: it runs on any JVM without the Android SDK.
// It compiles the game rules from ../core so it replays runs with exactly the app's engine.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "arrow3d-server"
