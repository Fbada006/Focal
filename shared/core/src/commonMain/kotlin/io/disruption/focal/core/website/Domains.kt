package io.disruption.focal.core.website

/**
 * Website rules for Block Lists (spec, "Domain matching").
 *
 * What the user types is cleaned up, not reinterpreted: scheme, user info, leading `www.`, port,
 * path, query and fragment are stripped and the rest lower-cased. The hostname is never reduced to
 * its registrable domain, so `docs.google.com` stays `docs.google.com`.
 */
object Domains {
    private const val MAX_LENGTH = 253
    private const val MAX_LABEL_LENGTH = 63

    /** Turn typed input, such as a pasted URL, into the hostname to block, or explain why not. */
    fun normalise(input: String): NormalisedDomain {
        var host = input.trim().lowercase()
        if (host.isEmpty()) return NormalisedDomain.Invalid(InvalidDomainReason.Empty)

        host = host.substringAfter("://", host)
        host = host.takeWhile { it != '/' && it != '?' && it != '#' }
        host = host.substringAfterLast('@')
        host = host.substringBefore(':')
        host = host.trimEnd('.')
        if (host.startsWith("www.") && host.count { it == '.' } >= 2) host = host.removePrefix("www.")

        val reason = invalidReason(host)
        return if (reason == null) NormalisedDomain.Valid(host) else NormalisedDomain.Invalid(reason)
    }

    /** The normalised hostname, or null if [input] isn't a usable domain. */
    fun normaliseOrNull(input: String): String? = (normalise(input) as? NormalisedDomain.Valid)?.hostname

    /**
     * Whether visiting [host] is blocked by [blocked]: it is the same hostname or a subdomain of it,
     * compared on whole labels (`m.youtube.com` matches `youtube.com`; `notyoutube.com` doesn't).
     * [host] may be a raw URL or hostname, as read from a browser.
     */
    fun matches(blocked: String, host: String): Boolean {
        val visited = normaliseOrNull(host) ?: return false
        return visited == blocked || visited.endsWith(".$blocked")
    }

    /** Whether any of [blocked] matches [host]. */
    fun anyMatches(blocked: Collection<String>, host: String): Boolean {
        val visited = normaliseOrNull(host) ?: return false
        return blocked.any { visited == it || visited.endsWith(".$it") }
    }

    private fun invalidReason(host: String): InvalidDomainReason? {
        if (host.isEmpty()) return InvalidDomainReason.Empty
        if (host.length > MAX_LENGTH) return InvalidDomainReason.TooLong
        val labels = host.split('.')
        if (labels.size < 2) return InvalidDomainReason.NoDot
        for (label in labels) {
            if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return InvalidDomainReason.BadLabel
            if (label.startsWith('-') || label.endsWith('-')) return InvalidDomainReason.BadLabel
            if (!label.all { it.isLetterOrDigit() || it == '-' }) return InvalidDomainReason.BadCharacter
        }
        return null
    }
}

sealed interface NormalisedDomain {
    data class Valid(val hostname: String) : NormalisedDomain
    data class Invalid(val reason: InvalidDomainReason) : NormalisedDomain
}

enum class InvalidDomainReason {
    Empty,
    NoDot,
    BadLabel,
    BadCharacter,
    TooLong,
}
