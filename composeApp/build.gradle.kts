import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.buildkonfig)

}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            compilations.all {

            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {

        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.androidDriver)
            implementation (libs.firebase.analytics.ktx.v2200)
            implementation (libs.androidx.connect.client)
            implementation(libs.androidx.appcompat)
            implementation(libs.firebase.appcheck.playintegrity)
            implementation(libs.integrity)
            implementation(libs.accompanist.drawablepainter)
            implementation(libs.google.firebase.crashlytics)
            implementation(libs.calf.filepicker)
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)






        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // SQLDelight
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.primitiveAdapters)
            // resources
            implementation(compose.components.resources)
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


android {
    namespace = "org.danielramzani.HealthCompose"
    compileSdk = 36

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "org.danielramzani.HealthCompose"
        minSdk = 31
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 11
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}
dependencies {
    implementation(libs.firebase.common.ktx)
    implementation(libs.play.services.measurement.api)
    implementation(libs.firebase.crashlytics)
}
