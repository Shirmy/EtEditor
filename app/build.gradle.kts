import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Copy
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class GenerateChangelogAssetTask : DefaultTask() {
    @get:InputFile
    abstract val changelogFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDir.file("changelog/CHANGELOG.md").get().asFile
        target.parentFile.mkdirs()
        changelogFile.get().asFile.copyTo(target, overwrite = true)
    }
}

val generateChangelogAsset by tasks.registering(GenerateChangelogAssetTask::class) {
    changelogFile.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
}

val signingPropertiesFile = rootProject.layout.projectDirectory.file("keystore.properties").asFile
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}

fun signingValue(name: String, envName: String): String {
    return providers.gradleProperty("eteditor.signing.$name").orNull
        ?: providers.environmentVariable(envName).orNull
        ?: signingProperties.getProperty(name).orEmpty()
}

val releaseStoreFile = signingValue("storeFile", "ETEDITOR_SIGNING_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "ETEDITOR_SIGNING_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ETEDITOR_SIGNING_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ETEDITOR_SIGNING_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() } && rootProject.file(releaseStoreFile).isFile

android {
    namespace = "com.eteditor"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.eteditor"
        minSdk = 26
        targetSdk = 36
        versionCode = 62
        versionName = "3.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val exportReleaseApk by tasks.registering(Copy::class) {
    dependsOn("clean", "assembleRelease")
    val releaseApkName = "EtEditor_${android.defaultConfig.versionName ?: "0.0"}.apk"
    val defaultExportDir = rootProject.layout.projectDirectory.dir("release-apk").asFile
    val exportDir = providers.gradleProperty("eteditor.exportDir")
        .map { rootProject.file(it) }
        .orElse(defaultExportDir)
    doFirst {
        val targetDir = exportDir.get().canonicalFile
        if (targetDir == defaultExportDir.canonicalFile) {
            targetDir.mkdirs()
            targetDir.listFiles { file ->
                file.isFile && file.extension.equals("apk", ignoreCase = true)
            }?.forEach { file ->
                if (file.name != releaseApkName) {
                    file.delete()
                }
            }
        }
    }
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include(releaseApkName)
    }
    into(exportDir)
    doLast {
        val apkFile = exportDir.get().resolve(releaseApkName)
        require(apkFile.isFile) {
            "Release APK was not exported: ${apkFile.absolutePath}"
        }

        ZipFile(apkFile).use { apk ->
            listOf("AndroidManifest.xml", "resources.arsc").forEach { entryName ->
                require(apk.getEntry(entryName) != null) {
                    "Exported APK is invalid, missing $entryName: ${apkFile.absolutePath}"
                }
            }
        }

        val androidHome = providers.environmentVariable("ANDROID_HOME").orNull
            ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
        val apksigner = androidHome
            ?.let { file(it).resolve("build-tools") }
            ?.takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?.resolve(if (System.getProperty("os.name").startsWith("Windows")) "apksigner.bat" else "apksigner")

        val apksignerFile = requireNotNull(apksigner?.takeIf { it.isFile }) {
            "Unable to find apksigner in ANDROID_HOME/ANDROID_SDK_ROOT."
        }

        val verifyProcess = ProcessBuilder(
            apksignerFile.absolutePath,
            "verify",
            "--verbose",
            apkFile.absolutePath,
        ).redirectErrorStream(true).start()
        val verifyOutput = verifyProcess.inputStream.bufferedReader().use { it.readText() }
        val verifyExitCode = verifyProcess.waitFor()
        if (verifyOutput.isNotBlank()) {
            logger.lifecycle(verifyOutput.trimEnd())
        }
        require(verifyExitCode == 0) {
            "apksigner verification failed with exit code $verifyExitCode: ${apkFile.absolutePath}"
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    mustRunAfter("clean")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateChangelogAsset,
            GenerateChangelogAssetTask::outputDir
        )
    }

    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.orElse("0.0").map { versionName ->
                    "EtEditor_$versionName.apk"
                }
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.sora.editor)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
