package io.disruption.focal.core.website

/** A popular distracting service and the domains it uses, offered as Suggested Websites. */
data class SuggestedService(
    val id: String,
    val name: String,
    /** Normalised hostnames; each also blocks its subdomains. */
    val domains: Set<String>,
    /** Android package names of the service's apps, so picking the app suggests its websites. */
    val androidPackages: Set<String> = emptySet(),
)

/** Focal's own curated list of Suggested Websites. */
interface SuggestedWebsitesCatalog {
    val services: List<SuggestedService>

    /** Services whose Android apps include any of [packages]. */
    fun forAndroidApps(packages: Collection<String>): List<SuggestedService> =
        services.filter { service -> packages.any { it in service.androidPackages } }
}
