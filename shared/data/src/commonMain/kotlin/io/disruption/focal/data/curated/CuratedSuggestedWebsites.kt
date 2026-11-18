package io.disruption.focal.data.curated

import io.disruption.focal.core.website.SuggestedService
import io.disruption.focal.core.website.SuggestedWebsitesCatalog

/**
 * Bundled Suggested Websites (story 21). Domains are normalised hostnames: a test checks each one
 * passes `Domains.normalise` unchanged. Keep the list to services people commonly block.
 */
object CuratedSuggestedWebsites : SuggestedWebsitesCatalog {
    override val services: List<SuggestedService> = listOf(
        service("instagram", "Instagram", setOf("instagram.com"), "com.instagram.android"),
        service("facebook", "Facebook", setOf("facebook.com", "fb.com"), "com.facebook.katana", "com.facebook.lite"),
        service("tiktok", "TikTok", setOf("tiktok.com"), "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        service("youtube", "YouTube", setOf("youtube.com", "youtu.be"), "com.google.android.youtube"),
        service("x", "X (Twitter)", setOf("x.com", "twitter.com"), "com.twitter.android"),
        service("reddit", "Reddit", setOf("reddit.com", "redd.it"), "com.reddit.frontpage"),
        service("snapchat", "Snapchat", setOf("snapchat.com"), "com.snapchat.android"),
        service("threads", "Threads", setOf("threads.net", "threads.com"), "com.instagram.barcelona"),
        service("bluesky", "Bluesky", setOf("bsky.app"), "xyz.blueskyweb.app"),
        service("pinterest", "Pinterest", setOf("pinterest.com"), "com.pinterest"),
        service("linkedin", "LinkedIn", setOf("linkedin.com"), "com.linkedin.android"),
        service("tumblr", "Tumblr", setOf("tumblr.com"), "com.tumblr"),
        service("discord", "Discord", setOf("discord.com"), "com.discord"),
        service("twitch", "Twitch", setOf("twitch.tv"), "tv.twitch.android.app"),
        service("netflix", "Netflix", setOf("netflix.com"), "com.netflix.mediaclient"),
        service("primevideo", "Prime Video", setOf("primevideo.com"), "com.amazon.avod.thirdpartyclient"),
        service("disneyplus", "Disney+", setOf("disneyplus.com"), "com.disney.disneyplus"),
        service("9gag", "9GAG", setOf("9gag.com"), "com.ninegag.android.app"),
        service("cnn", "CNN", setOf("cnn.com"), "com.cnn.mobile.android.phone"),
        service("bbcnews", "BBC News", setOf("bbc.com", "bbc.co.uk"), "bbc.mobile.news.ww"),
    )

    private fun service(id: String, name: String, domains: Set<String>, vararg packages: String) =
        SuggestedService(id, name, domains, packages.toSet())
}
