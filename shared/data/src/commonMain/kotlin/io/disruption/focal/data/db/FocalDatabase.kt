package io.disruption.focal.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Focal's on-device database (ADR 0009). The schema is exported to `shared/data/schemas` so
 * future versions can ship migrations. Until v1 is released, the schema stays at version 1.
 */
@Database(
    entities = [BlockListEntity::class, FocusSessionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(FocalDatabaseConstructor::class)
abstract class FocalDatabase : RoomDatabase() {
    abstract fun blockListDao(): BlockListDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val FILE_NAME = "focal.db"
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("KotlinNoActualForExpect")
expect object FocalDatabaseConstructor : RoomDatabaseConstructor<FocalDatabase> {
    override fun initialize(): FocalDatabase
}

/** Finish a platform's database builder the same way on Android and iOS. */
fun RoomDatabase.Builder<FocalDatabase>.buildFocalDatabase(): FocalDatabase = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
