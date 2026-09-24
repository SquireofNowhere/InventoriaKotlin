pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // The Kotlin/Wasm toolchain (Node.js, Yarn, Binaryen) for :shared and :webApp. The Kotlin
        // plugin would add these as project repositories, which FAIL_ON_PROJECT_REPOS rejects, so
        // they are declared here instead and the plugin's own copies are switched off in the root
        // build file.
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen distributions"
            patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

// No spaces: Kotlin/Wasm derives npm package names from this, and npm rejects them.
rootProject.name = "InventoriaKotlin"
// The Android app needs an Android SDK to even configure. `-Pinventoria.webOnly=true` leaves it
// out so the shared module and the web app can be built on a machine (or CI job) without one.
if (providers.gradleProperty("inventoria.webOnly").orNull != "true") {
    include(":app")
}
include(":shared")
include(":webApp")
 