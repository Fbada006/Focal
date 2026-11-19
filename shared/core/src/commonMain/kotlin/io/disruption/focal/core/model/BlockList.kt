package io.disruption.focal.core.model

/** A named, reusable set of apps, App Categories and websites that are blocked together. */
data class BlockList(
    val id: BlockListId,
    val name: String,
    val blocked: BlockedSet,
)
