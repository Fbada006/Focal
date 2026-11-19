package io.disruption.focal.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.disruption.focal.data.db.FocalDatabase

actual fun inMemoryDatabase(): FocalDatabase =
    Room.inMemoryDatabaseBuilder<FocalDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
