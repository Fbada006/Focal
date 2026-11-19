package io.disruption.focal.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object BlockLists

@Serializable
data class BlockListEditor(val id: String)

@Serializable
data object Home

@Serializable
data object StartSession
