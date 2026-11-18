package io.disruption.focal

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform