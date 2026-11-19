package io.disruption.focal.data.curated

import io.disruption.focal.core.website.Domains
import io.disruption.focal.core.website.NormalisedDomain
import kotlin.test.Test
import kotlin.test.assertEquals

class CuratedSuggestedWebsitesTest {
    private val services = CuratedSuggestedWebsites.services

    @Test
    fun everyDomainPassesNormalisationUnchanged() {
        for (domain in services.flatMap { it.domains }) {
            assertEquals(NormalisedDomain.Valid(domain), Domains.normalise(domain), domain)
        }
    }

    @Test
    fun idsDomainsAndPackagesAreUnique() {
        assertEquals(services.size, services.map { it.id }.toSet().size)
        val domains = services.flatMap { it.domains }
        assertEquals(domains.size, domains.toSet().size, "a domain belongs to one service")
        val packages = services.flatMap { it.androidPackages }
        assertEquals(packages.size, packages.toSet().size, "a package belongs to one service")
    }

    @Test
    fun findsServicesForPickedAndroidApps() {
        val found = CuratedSuggestedWebsites.forAndroidApps(listOf("com.instagram.android", "com.example.other"))
        assertEquals(listOf("instagram"), found.map { it.id })
    }
}
