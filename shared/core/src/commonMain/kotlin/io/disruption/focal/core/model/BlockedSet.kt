package io.disruption.focal.core.model

import io.disruption.focal.core.website.Domains

/**
 * What a Block List, or a running Focus Session's own copy of it, blocks.
 *
 * App and App Category identifiers are opaque to the domain: package names and category keys on
 * Android, serialised Screen Time tokens on iOS. Domains are normalised hostnames (see
 * [Domains.normalise]); a hostname also blocks its subdomains.
 */
data class BlockedSet(
    val apps: Set<String> = emptySet(),
    val appCategories: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = apps.isEmpty() && appCategories.isEmpty() && domains.isEmpty()

    operator fun plus(other: BlockedSet): BlockedSet = BlockedSet(
        apps = apps + other.apps,
        appCategories = appCategories + other.appCategories,
        domains = domains + other.domains,
    )

    operator fun minus(other: BlockedSet): BlockedSet = BlockedSet(
        apps = apps - other.apps,
        appCategories = appCategories - other.appCategories,
        domains = domains - other.domains,
    )

    /** Whether visiting [host] (a hostname or URL) is blocked by one of [domains]. */
    fun blocksWebsite(host: String): Boolean = Domains.anyMatches(domains, host)

    /** True when everything in [other] is also blocked by this set. */
    fun containsAll(other: BlockedSet): Boolean =
        apps.containsAll(other.apps) &&
            appCategories.containsAll(other.appCategories) &&
            domains.containsAll(other.domains)

    companion object {
        val Empty = BlockedSet()
    }
}
