import com.android.build.api.artifact.SingleArtifact
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}

abstract class VerifyOfflineManifest : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val document = factory.newDocumentBuilder().parse(manifestFile.get().asFile)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val forbidden = setOf(
            "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE", "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE", "android.permission.NEARBY_WIFI_DEVICES",
        )
        for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
            val permissions = document.getElementsByTagName(tag)
            for (index in 0 until permissions.length) {
                val name = (permissions.item(index) as Element).getAttributeNS(androidNamespace, "name")
                check(name !in forbidden) { "Offline app must not request $name" }
            }
        }
        val application = document.getElementsByTagName("application").item(0) as Element
        check(application.getAttributeNS(androidNamespace, "allowBackup") == "false") {
            "Automatic system backup must remain disabled"
        }
        check(application.getAttributeNS(androidNamespace, "fullBackupContent") == "false") {
            "Legacy automatic backup must remain disabled"
        }
        check(application.getAttributeNS(androidNamespace, "dataExtractionRules") == "@xml/data_extraction_rules") {
            "Explicit data extraction exclusions are required"
        }
        logger.lifecycle("Offline policy verified: ${manifestFile.get().asFile.name}")
    }
}

android {
    namespace = "com.pamurlykin.sportsactivityassistant"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.pamurlykin.sportsactivityassistant"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "1.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

android.sourceSets.getByName("androidTest").assets.directories.add(layout.projectDirectory.dir("schemas").asFile.path)

dependencies {
    val composeBom = platform(libs.compose.bom)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.navigation.compose)

    implementation(libs.material)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}

val verifyOfflinePolicy = tasks.register("verifyOfflinePolicy") {
    group = "verification"
    description = "Checks merged debug and release manifests for the offline-only policy."
}
androidComponents.onVariants(androidComponents.selector().all()) { variant ->
    val variantTitle = variant.name.replaceFirstChar { it.uppercase() }
    val verifyManifest = tasks.register<VerifyOfflineManifest>("verify${variantTitle}OfflineManifest") {
        manifestFile.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
    }
    verifyOfflinePolicy.configure { dependsOn(verifyManifest) }
}
