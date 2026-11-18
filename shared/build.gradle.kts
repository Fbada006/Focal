import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Umbrella module (ADR 0010). It depends on `core`, `data` and `ui` and exports all three as the
// single `Shared` iOS framework, because iOS can only consume one Kotlin framework without
// duplicating the Kotlin runtime and types. Android depends on it too, so both apps see the same
// surface.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(project(":shared:core"))
            export(project(":shared:data"))
            export(project(":shared:ui"))
        }
    }
    android {
        namespace = "io.disruption.focal.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(project(":shared:data"))
            api(project(":shared:ui"))
            api(libs.koin.core)
        }
    }
}
