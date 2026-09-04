import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.tasks.Sync

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("../../android/keystore.properties")
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
}

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.whappy.chat"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        applicationId = "com.whappy.chat"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            check(keystorePropertiesFile.exists()) { "Clé de publication WAPI introuvable." }
            storeFile = rootProject.file("../../android/${keystoreProperties.getProperty("storeFile")}")
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Flutter release builds are intended to run through R8. Keeping
            // it disabled retained unused Firebase/Android bytecode in every
            // APK and made WAPI considerably heavier than necessary.
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    // WAPI Pool uses the tested OpenGL renderer that pre-dates the Flutter
    // migration.  Keep a single source of truth in the native game module and
    // copy only the renderer/rules that the Flutter platform view needs at
    // build time.  Importing the whole legacy Android application would also
    // pull its obsolete Compose screens into the Flutter APK.
    sourceSets {
        getByName("main").java.srcDir(layout.buildDirectory.dir("generated/source/wapiPool3d/main/kotlin"))
        getByName("main").res.srcDir(layout.buildDirectory.dir("generated/res/wapiPool3d/main"))
    }
}

val prepareWapiPool3d by tasks.registering(Sync::class) {
    from("../../../android/app/src/main/java/com/whappy/chat") {
        include("WapiTabletop3DView.kt")
        include("WapiPoolPresentation.kt")
        include("WapiTabletopInput.kt")
        include("WapiTabletopPicking.kt")
        include("WapiGameRules.kt")
    }
    into(layout.buildDirectory.dir("generated/source/wapiPool3d/main/kotlin/com/whappy/chat"))
}

val prepareWapiPool3dTextures by tasks.registering(Sync::class) {
    from("../../../android/app/src/main/res/drawable-nodpi") {
        include("wapi_game_walnut_texture.webp")
        include("wapi_game_felt_texture.webp")
        include("wapi_game_felt_texture_competition.png")
    }
    into(layout.buildDirectory.dir("generated/res/wapiPool3d/main/drawable-nodpi"))
}

// Short game effects are bundled as native raw resources so SoundPool can
// play them with low latency even after a call or a media player changed the
// Flutter audio session.
val prepareWapiPool3dAudio by tasks.registering(Sync::class) {
    from("../../../android/app/src/main/res/raw") {
        include("wapi_pool_cue.wav")
        include("wapi_pool_collision.wav")
        include("wapi_pool_cushion.wav")
        include("wapi_pool_pocket.wav")
        include("wapi_piece_move.wav")
        include("wapi_piece_select.wav")
        include("wapi_piece_capture.wav")
        include("wapi_piece_crown.wav")
        include("wapi_dice_roll.wav")
        include("wapi_victory.wav")
    }
    into(layout.buildDirectory.dir("generated/res/wapiPool3d/main/raw"))
}

tasks.configureEach {
    if (name != "prepareWapiPool3d" && name != "prepareWapiPool3dTextures" && name != "prepareWapiPool3dAudio" &&
        (name.contains("Kotlin", ignoreCase = true) ||
            name.contains("Resource", ignoreCase = true) ||
            name.contains("SourceSet", ignoreCase = true))) {
        dependsOn(prepareWapiPool3d)
        dependsOn(prepareWapiPool3dTextures)
        dependsOn(prepareWapiPool3dAudio)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
