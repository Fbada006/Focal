package io.disruption.focal.data.curated

import io.disruption.focal.core.apps.AppCategory
import io.disruption.focal.core.apps.AppCategory.Dating
import io.disruption.focal.core.apps.AppCategory.Games
import io.disruption.focal.core.apps.AppCategory.Music
import io.disruption.focal.core.apps.AppCategory.News
import io.disruption.focal.core.apps.AppCategory.Shopping
import io.disruption.focal.core.apps.AppCategory.Social
import io.disruption.focal.core.apps.AppCategory.Video

/**
 * Bundled package name → App Category data for popular apps (Android). It wins over Android's own
 * `ApplicationInfo.category`, which many apps leave unset.
 */
object CuratedAppCategories {
    val byPackage: Map<String, AppCategory> = mapOf(
        // Social
        "com.instagram.android" to Social,
        "com.facebook.katana" to Social,
        "com.facebook.lite" to Social,
        "com.twitter.android" to Social,
        "com.reddit.frontpage" to Social,
        "com.snapchat.android" to Social,
        "com.instagram.barcelona" to Social,
        "xyz.blueskyweb.app" to Social,
        "com.pinterest" to Social,
        "com.linkedin.android" to Social,
        "com.tumblr" to Social,
        "com.discord" to Social,
        "com.ninegag.android.app" to Social,
        // Video
        "com.zhiliaoapp.musically" to Video,
        "com.ss.android.ugc.trill" to Video,
        "com.google.android.youtube" to Video,
        "tv.twitch.android.app" to Video,
        "com.netflix.mediaclient" to Video,
        "com.amazon.avod.thirdpartyclient" to Video,
        "com.disney.disneyplus" to Video,
        // News
        "com.cnn.mobile.android.phone" to News,
        "bbc.mobile.news.ww" to News,
        "com.google.android.apps.magazines" to News,
        "flipboard.app" to News,
        // Shopping
        "com.amazon.mShop.android.shopping" to Shopping,
        "com.ebay.mobile" to Shopping,
        "com.einnovation.temu" to Shopping,
        "com.zzkko" to Shopping,
        "com.alibaba.aliexpresshd" to Shopping,
        // Music & audio
        "com.spotify.music" to Music,
        "com.google.android.apps.youtube.music" to Music,
        "com.soundcloud.android" to Music,
        // Dating
        "com.tinder" to Dating,
        "com.bumble.app" to Dating,
        "co.hinge.app" to Dating,
        // Games
        "com.supercell.clashofclans" to Games,
        "com.king.candycrushsaga" to Games,
        "com.roblox.client" to Games,
        "com.mojang.minecraftpe" to Games,
    )
}
