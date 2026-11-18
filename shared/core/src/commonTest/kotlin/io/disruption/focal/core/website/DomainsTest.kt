package io.disruption.focal.core.website

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainsTest {

    @Test
    fun normalisesTypedInput() {
        val cases = listOf(
            "youtube.com" to "youtube.com",
            "YouTube.COM" to "youtube.com",
            "  youtube.com  " to "youtube.com",
            "https://www.youtube.com/watch?v=abc" to "youtube.com",
            "http://youtube.com:8080/" to "youtube.com",
            "www.youtube.com" to "youtube.com",
            "m.youtube.com" to "m.youtube.com",
            "docs.google.com" to "docs.google.com",
            "https://docs.google.com/document/d/1" to "docs.google.com",
            "youtube.com#top" to "youtube.com",
            "youtube.com?x=1" to "youtube.com",
            "user:pass@reddit.com/r/all" to "reddit.com",
            "news.ycombinator.com." to "news.ycombinator.com",
            "www.bbc.co.uk" to "bbc.co.uk",
            "app.www.example.com" to "app.www.example.com",
            "x-y.example" to "x-y.example",
        )
        for ((input, expected) in cases) {
            assertEquals(NormalisedDomain.Valid(expected), Domains.normalise(input), "input: $input")
        }
    }

    @Test
    fun rejectsInvalidInput() {
        val cases = listOf(
            "" to InvalidDomainReason.Empty,
            "   " to InvalidDomainReason.Empty,
            "https://" to InvalidDomainReason.Empty,
            "youtube" to InvalidDomainReason.NoDot,
            "you tube.com" to InvalidDomainReason.BadCharacter,
            "you_tube.com" to InvalidDomainReason.BadCharacter,
            "youtube..com" to InvalidDomainReason.BadLabel,
            ".youtube.com" to InvalidDomainReason.BadLabel,
            "-youtube.com" to InvalidDomainReason.BadLabel,
            "youtube-.com" to InvalidDomainReason.BadLabel,
            "${"a".repeat(64)}.com" to InvalidDomainReason.BadLabel,
            "${"abcdefghi.".repeat(26)}com" to InvalidDomainReason.TooLong,
        )
        for ((input, reason) in cases) {
            assertEquals(NormalisedDomain.Invalid(reason), Domains.normalise(input), "input: $input")
        }
    }

    @Test
    fun aTwoLabelWwwHostIsKeptAsTyped() {
        assertEquals(NormalisedDomain.Valid("www.com"), Domains.normalise("www.com"))
    }

    @Test
    fun matchesTheHostnameAndItsSubdomainsOnWholeLabels() {
        val cases = listOf(
            Triple("youtube.com", "youtube.com", true),
            Triple("youtube.com", "m.youtube.com", true),
            Triple("youtube.com", "www.youtube.com", true),
            Triple("youtube.com", "a.b.youtube.com", true),
            Triple("youtube.com", "https://m.youtube.com/shorts", true),
            Triple("youtube.com", "YOUTUBE.COM", true),
            Triple("youtube.com", "notyoutube.com", false),
            Triple("youtube.com", "youtube.com.evil.example", false),
            Triple("youtube.com", "youtube.co", false),
            Triple("docs.google.com", "docs.google.com", true),
            Triple("docs.google.com", "mail.google.com", false),
            Triple("docs.google.com", "google.com", false),
            Triple("youtube.com", "not a url", false),
        )
        for ((blocked, visited, expected) in cases) {
            assertEquals(expected, Domains.matches(blocked, visited), "$blocked vs $visited")
        }
    }

    @Test
    fun anyMatchesChecksEveryBlockedDomain() {
        val blocked = setOf("reddit.com", "docs.google.com")
        assertEquals(true, Domains.anyMatches(blocked, "old.reddit.com"))
        assertEquals(false, Domains.anyMatches(blocked, "mail.google.com"))
    }
}
