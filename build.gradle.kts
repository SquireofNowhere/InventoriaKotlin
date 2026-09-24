// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt.android) apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// The Wasm toolchain repositories are declared in settings.gradle.kts; stop the Kotlin plugin from
// adding its own project-level copies, which FAIL_ON_PROJECT_REPOS would reject. A null base URL
// means "add no repository"; the convention has to go too, or set(null) just falls back to it.
// Node and Yarn are set up on the root project, Binaryen on each Wasm module, hence allprojects.
fun Property<String>.disableDownloadRepository() {
    convention(null as String?)
    set(null as String?)
}
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().downloadBaseUrl.disableDownloadRepository()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>().downloadBaseUrl.disableDownloadRepository()
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().downloadBaseUrl.disableDownloadRepository()
    }
}
