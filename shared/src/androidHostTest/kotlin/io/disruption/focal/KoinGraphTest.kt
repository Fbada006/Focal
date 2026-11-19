package io.disruption.focal

import android.content.Context
import io.disruption.focal.ui.di.androidUiModule
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * The Android graph as `FocalApplication` starts it. Koin's verify checks that every definition's
 * constructor dependencies are declared, without running anything, so a missing or mismatched
 * definition fails here instead of crashing Focal at launch.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {
    @Test
    fun everyDefinitionCanBeBuilt() {
        val graph = module {
            includes(focalModules(PendingBlockingEngine(), PendingPermissionGateway()) + androidUiModule)
        }
        graph.verify(
            extraTypes = listOf(
                // Supplied by the platform: androidContext(...) in FocalApplication.
                Context::class,
                // Passed straight into a definition, not looked up: verify reads constructors only.
                Map::class, // AppCategoryResolver(CuratedAppCategories.byPackage)
            ),
        )
    }
}
