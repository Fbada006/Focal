package io.disruption.focal.testing

import io.disruption.focal.core.website.SuggestedService
import io.disruption.focal.core.website.SuggestedWebsitesCatalog

/** A small, stable catalog for tests. */
object FakeSuggestedWebsites : SuggestedWebsitesCatalog {
    override val services = listOf(
        SuggestedService("instagram", "Instagram", setOf("instagram.com"), setOf("com.instagram.android")),
        SuggestedService("youtube", "YouTube", setOf("youtube.com", "youtu.be"), setOf("com.google.android.youtube")),
    )
}
