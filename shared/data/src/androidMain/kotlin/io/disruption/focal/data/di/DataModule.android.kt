package io.disruption.focal.data.di

import android.content.Context
import androidx.room.Room
import io.disruption.focal.data.db.FocalDatabase
import io.disruption.focal.data.db.buildFocalDatabase
import io.disruption.focal.data.preferences.DataStoreFocalPreferences
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformDataModule(): Module = module {
    single<FocalDatabase> {
        val context = get<Context>()
        Room.databaseBuilder<FocalDatabase>(
            context = context,
            name = context.getDatabasePath(FocalDatabase.FILE_NAME).absolutePath,
        ).buildFocalDatabase()
    }
    single {
        val context = get<Context>()
        DataStoreFocalPreferences.createDataStore(
            context.filesDir.resolve(DataStoreFocalPreferences.FILE_NAME).absolutePath,
        )
    }
}
