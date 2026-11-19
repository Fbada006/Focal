package io.disruption.focal.ui

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform