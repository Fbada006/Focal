package io.disruption.focal.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "block_lists")
data class BlockListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val apps: Set<String>,
    val appCategories: Set<String>,
    val domains: Set<String>,
)

/**
 * A Focus Session, running or finished. The blocked set is the session's own copy (ADR 0007).
 * Instants are stored as epoch milliseconds; enums by name.
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index("outcome"), Index("endedAt")],
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val blockListId: String,
    val name: String,
    val strictness: String,
    val startedAt: Long,
    val plannedEnd: Long,
    val apps: Set<String>,
    val appCategories: Set<String>,
    val domains: Set<String>,
    val scheduleId: String?,
    val breaks: List<BreakRecord>,
    val endedAt: Long?,
    val outcome: String?,
)

@Serializable
data class BreakRecord(val startedAt: Long, val endsAt: Long)
