package io.disruption.focal.ui.blocklists

import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.website.InvalidDomainReason

internal fun BlockedSet.summary(): String {
    val parts = buildList {
        if (apps.isNotEmpty()) add(count(apps.size, "app", "apps"))
        if (appCategories.isNotEmpty()) add(count(appCategories.size, "category", "categories"))
        if (domains.isNotEmpty()) add(count(domains.size, "website", "websites"))
    }
    return if (parts.isEmpty()) "Nothing blocked yet" else parts.joinToString(" · ")
}

internal fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

internal fun InvalidDomainReason.message(): String = when (this) {
    InvalidDomainReason.Empty -> "Type a website, such as youtube.com"
    InvalidDomainReason.NoDot -> "Include the ending, such as .com"
    InvalidDomainReason.BadLabel, InvalidDomainReason.BadCharacter -> "That doesn't look like a website address"
    InvalidDomainReason.TooLong -> "That address is too long"
}

internal fun inUseMessage(sessionNames: List<String>): String =
    "This Block List can't be deleted while ${sessionNames.joinToString(", ")} " +
        "${if (sessionNames.size == 1) "is" else "are"} running. Delete it after the session ends."

internal fun deferredMessage(sessionNames: List<String>): String {
    val names = sessionNames.distinct()
    return "Removals take effect after ${names.joinToString(" and ")} " +
        "${if (names.size == 1) "ends" else "end"}. Additions apply now."
}
