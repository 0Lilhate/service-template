pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "service-loader-pzp"

include(
    "service-loader-pzp-api",
    "service-loader-pzp-app",
    "service-loader-pzp-db",
)
