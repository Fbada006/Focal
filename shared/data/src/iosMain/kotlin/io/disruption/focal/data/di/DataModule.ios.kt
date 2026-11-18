package io.disruption.focal.data.di

import androidx.room.Room
import io.disruption.focal.data.db.FocalDatabase
import io.disruption.focal.data.db.buildFocalDatabase
import io.disruption.focal.data.preferences.DataStoreFocalPreferences
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun platformDataModule(): Module = module {
    single<FocalDatabase> {
        Room.databaseBuilder<FocalDatabase>(name = "${appSupportDirectory()}/${FocalDatabase.FILE_NAME}")
            .buildFocalDatabase()
    }
    single {
        DataStoreFocalPreferences.createDataStore("${appSupportDirectory()}/${DataStoreFocalPreferences.FILE_NAME}")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun appSupportDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "No Application Support directory" }
}
