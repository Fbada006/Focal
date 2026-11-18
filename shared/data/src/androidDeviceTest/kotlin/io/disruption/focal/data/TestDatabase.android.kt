package io.disruption.focal.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import io.disruption.focal.data.db.FocalDatabase

actual fun inMemoryDatabase(): FocalDatabase =
    Room.inMemoryDatabaseBuilder<FocalDatabase>(InstrumentationRegistry.getInstrumentation().targetContext)
        .setDriver(BundledSQLiteDriver())
        .build()
