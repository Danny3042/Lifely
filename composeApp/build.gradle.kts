import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties

// Shared KMP module. Under AGP 9 it can no longer apply com.android.application,
// so it is an Android-KMP library and the APK is built by :androidApp.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
    // google-services + crashlytics moved to :androidApp (they need the application plugin).
    // sqlDelight plugin removed: there are no .sq files, only the runtime/driver libraries are used.
}

kotlin {
    compilerOptions {
        // The project uses expect/actual classes (still Beta in Kotlin); silence the per-file warning
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        // Same namespace as before, so existing `org.danielramzani.HealthCompose.R` imports keep working.
        namespace = "org.danielramzani.HealthCompose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // Needed for src/androidMain/res and Compose Multiplatform resources.
        androidResources {
            enable = true
        }
    }

    // iosX64 (Intel simulator) removed: Compose Multiplatform 1.12 no longer publishes it.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "org.danielramzani.HealthCompose.ComposeApp")
        }
    }

    sourceSets {
        // kotlinx-datetime 0.7 moved Clock/Instant to kotlin.time
        all { languageSettings.optIn("kotlin.time.ExperimentalTime") }


        androidMain.dependencies {
            // GitLive firebase-* 2.x takes the Android Firebase SDK versions from the BoM
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.firebase.analytics.ktx.v2200)
            implementation(libs.androidx.connect.client)
            implementation(libs.androidx.appcompat)
            implementation(libs.firebase.appcheck.playintegrity)
            implementation(libs.integrity)
            implementation(libs.accompanist.drawablepainter)
            implementation(libs.google.firebase.crashlytics)
            implementation(libs.calf.filepicker)
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)
            // Previously in the top-level dependencies {} block
            implementation(libs.firebase.common.ktx)
            implementation(libs.play.services.measurement.api)
            implementation(libs.firebase.crashlytics)
        }
        commonMain.dependencies {
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.components.resources)
            implementation(libs.cmp.ui.tooling.preview)
            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // SQLDelight
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.primitiveAdapters)
            // Firebase
            implementation(libs.firebase.auth)
            api(libs.kmpnotifier)
            implementation(libs.jetbrains.navigation.compose)
            // voyager TabNav
            implementation(libs.voyager.tabNavigator)
            // extended icons
            implementation(libs.material.icons.extended)
            implementation(libs.kotlinx.datetime)
            // Health APIs
            implementation(libs.ktor.client.logging)
            implementation(libs.generativeai)
            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.kmpauth.google) //Google One Tap Sign-In
            implementation(libs.kmpauth.firebase) //Integrated Authentications with Firebase
            implementation(libs.kmpauth.uihelper) //UiHelper SignIn buttons (AppleSignIn, GoogleSignInButton)
            // Still uses the plugin alias so the plugin picks the Material3 build that matches this CMP release
            implementation(compose.material3)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.health.kmp)
            // Gemini API
            implementation(libs.generativeai.google)
            implementation(libs.calf.filepicker)
            api(libs.kmpnotifier.v121) // in iOS export this library
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // SQLDelight
            implementation(libs.sqldelight.nativeDriver)
            implementation(libs.ktor.client.darwin.v2310)
            implementation(libs.calf.filepicker)
        }
    }
}

buildkonfig {
    packageName = "org.danielramzani.HealthCompose"

    val localProperties =
        Properties().apply {
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                load(propsFile.inputStream())
            }
        }

    defaultConfigs {
        buildConfigField(
            FieldSpec.Type.STRING,
            "GEMINI_API_KEY",
            localProperties["gemini_api_key"]?.toString() ?: "",
        )
        buildConfigField(
            FieldSpec.Type.BOOLEAN,
            "DEV_MODE",
            localProperties["dev_mode"]?.toString() ?: "false",
        )
    }
}

dependencies {
    // The Android-KMP library plugin has no build types, so debugImplementation is replaced by this
    // (keeps Compose previews working in Android Studio).
    "androidRuntimeClasspath"(libs.compose.ui.tooling)
}

// Target-level compilerOptions isn't available in the androidLibrary {} DSL, so set the JVM target on the tasks.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
