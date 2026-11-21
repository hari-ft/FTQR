import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val versionFile = rootProject.file("version.properties")
if (!versionFile.isFile) throw GradleException("version.properties is missing: expected major/minor/patch")
val versionProps = Properties().apply { versionFile.inputStream().use { load(it) } }
fun versionPart(key: String, max: Int) = versionProps.getProperty(key)?.trim()?.toIntOrNull()?.takeIf { it in 0..max }
    ?: throw GradleException("version.properties: '$key' must be a number from 0 to $max")
val major = versionPart("major", 99)
val minor = versionPart("minor", 9)
val patch = versionPart("patch", 9)
val appVersionName = "$major.$minor.$patch"
val appVersionCode = major * 1_000_000 + minor * 1_000 + patch

val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun signingValue(key: String): String? = signingProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

val releaseSigning: ReleaseSigning? = run {
    val storeFile = signingValue("storeFile")?.let { rootProject.file(it) }?.takeIf { it.isFile }
    val storePassword = signingValue("storePassword")
    val keyAlias = signingValue("keyAlias")
    val keyPassword = signingValue("keyPassword")
    if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
        ReleaseSigning(storeFile, storePassword, keyAlias, keyPassword)
    } else {
        null
    }
}

val releaseRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (releaseRequested && releaseSigning == null) {
    throw GradleException("Release signing key missing: expected keystore.properties with storeFile, storePassword, keyAlias, keyPassword")
}

android {
    namespace = "com.hari.ftqr"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hari.ftqr"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/NOTICE.md", "META-INF/LICENSE.md")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val camerax = "1.7.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-mlkit-vision:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("com.google.zxing:core:3.5.4")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
}

val releaseOut = rootProject.file("build/Release")
val debugOut = rootProject.file("build/Debug")

fun registerApkCopy(taskName: String, variant: String, outDir: File) = tasks.register<Copy>(taskName) {
    val apkName = "FTQR-$appVersionName-$variant.apk"
    val suffix = "-$variant.apk"
    val target = outDir
    from(layout.buildDirectory.dir("outputs/apk/$variant")) { include("app-$variant.apk") }
    into(target)
    rename { apkName }
    doLast {
        target.listFiles { f -> f.isFile && f.name.startsWith("FTQR-") && f.name.endsWith(suffix) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(3)
            ?.forEach { if (it.delete()) println("Removed old build ${it.name}") }
    }
}

val copyReleaseApk = registerApkCopy("copyReleaseApk", "release", releaseOut)
val copyDebugApk = registerApkCopy("copyDebugApk", "debug", debugOut)

tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(copyDebugApk) }
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApk) }

fun registerBump(name: String, next: Triple<Int, Int, Int>) = tasks.register(name) {
    group = "versioning"
    description = "Set the version to ${next.first}.${next.second}.${next.third}"
    val file = versionFile
    val current = appVersionName
    val nextName = "${next.first}.${next.second}.${next.third}"
    val nextContent = listOf("major=${next.first}", "minor=${next.second}", "patch=${next.third}").joinToString("\n", postfix = "\n")
    val tooHigh = next.first > 99
    doLast {
        if (tooHigh) throw GradleException("Version $nextName is past the limit 99.9.9")
        file.writeText(nextContent)
        println("Version: $current -> $nextName")
    }
}
registerBump("bumpPatch", when {
    patch < 9 -> Triple(major, minor, patch + 1)
    minor < 9 -> Triple(major, minor + 1, 0)
    else -> Triple(major + 1, 0, 0)
})
registerBump("bumpMinor", if (minor < 9) Triple(major, minor + 1, 0) else Triple(major + 1, 0, 0))
registerBump("bumpMajor", Triple(major + 1, 0, 0))
