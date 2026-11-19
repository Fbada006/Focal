// Test doubles for the domain core: a settable clock, a recording BlockingEngine and in-memory
// repositories. Used by the tests of `core`, `data` and `ui`; never by production code.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
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
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(libs.kotlin.test)
            api(libs.kotlinx.coroutines.test)
        }
        // kotlin-test in a main source set doesn't get the JUnit variant inferred, so on JVM
        // `@Test` and the asserter would be missing. Name it explicitly.
        jvmMain.dependencies {
            api(libs.kotlin.testJunit)
        }
    }
}
