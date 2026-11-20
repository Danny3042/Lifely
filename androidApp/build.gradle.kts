import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Android application entry point. AGP 9 has Kotlin built in, so no kotlin-android plugin is needed.
// The app code (MainActivity, receivers, widget, Application class) lives in :composeApp's androidMain
// and is merged in through the dependency below.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.google.firebase.crashlytics)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    // Must differ from :composeApp's namespace; the applicationId (what Play / Firebase see) is unchanged.
    namespace = "org.danielramzani.HealthCompose.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.danielramzani.HealthCompose"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 13
        versionName = "1.0"
    }

    // Load keystore properties from a root-level `keystore.properties` file (keep this out of VCS)
    val keystoreProperties = Properties().apply {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) load(propsFile.inputStream())
    }

    signingConfigs {
        create("release") {
            // If the file/values are missing the values default to empty strings to avoid build crashes
            storeFile = keystoreProperties["storeFile"]?.toString()?.let { file(it) }
            storePassword = keystoreProperties["storePassword"]?.toString() ?: ""
            keyAlias = keystoreProperties["keyAlias"]?.toString() ?: ""
            keyPassword = keystoreProperties["keyPassword"]?.toString() ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Use the release signing config when present
            signingConfig = signingConfigs.findByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.firebase.crashlytics)
    debugImplementation(libs.compose.ui.tooling)
}
