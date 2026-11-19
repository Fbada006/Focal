// Pure Kotlin domain layer (ADR 0010).
//
// `core` deliberately has a plain `jvm()` target instead of an Android target, and no Compose,
// Room or AndroidX dependencies. That makes "no platform imports" a compile error rather than a
// convention: `android.*` and `platform.UIKit.*` are not on its classpath. Android modules consume
// the JVM variant, the same way they consume kotlinx-coroutines.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
    }
}
