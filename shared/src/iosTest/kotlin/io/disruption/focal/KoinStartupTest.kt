package io.disruption.focal

import io.disruption.focal.data.db.FocalDatabase
import io.disruption.focal.data.preferences.DataStoreFocalPreferences
import io.disruption.focal.ui.di.iosUiModule
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Starts Koin exactly as iOS does, with the real Room database and DataStore, and builds every
 * definition, so a missing or mismatched one fails here instead of crashing Focal at launch.
 * Android runs the same shared modules; `KoinGraphTest` adds a static check of its own modules.
 */
@OptIn(KoinInternalApi::class)
class KoinStartupTest {
    @BeforeTest
    fun freshStorage() = deleteStorage()

    @AfterTest
    fun stop() {
        stopKoin()
        deleteStorage()
    }

    @Test
    fun everyDefinitionResolves() {
        val koin = initKoin(PendingBlockingEngine(), PendingPermissionGateway(), listOf(iosUiModule)).koin
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
        assertTrue(definitions.size > 20, "expected the whole graph, found ${definitions.size}")

        val failures = definitions.mapNotNull { definition ->
            // Screens with an id get one; the rest ignore the parameter.
            runCatching { koin.get<Any>(definition.primaryType, definition.qualifier) { parametersOf("id") } }
                .exceptionOrNull()
                ?.let { "${definition.primaryType.simpleName}: ${it.message}" }
        }
        assertTrue(failures.isEmpty(), "Koin can't build:\n" + failures.joinToString("\n"))
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun deleteStorage() {
        val files = NSFileManager.defaultManager
        val dir = files.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)?.path ?: return
        val db = FocalDatabase.FILE_NAME
        for (name in listOf(db, "$db-wal", "$db-shm", DataStoreFocalPreferences.FILE_NAME)) {
            files.removeItemAtPath("$dir/$name", null)
        }
    }
}
