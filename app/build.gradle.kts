plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    id("com.google.gms.google-services")
}

val envFile = project.rootProject.file(".env")
val envMap = mutableMapOf<String, String>()
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        if (line.isNotBlank() && !line.startsWith("#")) {
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                envMap[parts[0].trim()] = parts[1].trim()
            }
        }
    }
}

android {
    namespace = "com.inventoria.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.inventoria.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.22"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "DEFAULT_WEB_CLIENT_ID", "\"${envMap["DEFAULT_WEB_CLIENT_ID"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${envMap["FIREBASE_DATABASE_URL"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"${envMap["FIREBASE_STORAGE_BUCKET"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kapt {
    correctErrorTypes = true
    javacOptions {
        option("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
        option("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Compose
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.tooling)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.google.play.services.auth)

    // Google Play Services
    implementation(libs.google.play.services.location)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore & Preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.preference.ktx)

    // Maps & UI
    implementation(libs.osmdroid)
    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
