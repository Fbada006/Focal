package io.disruption.focal.ui

class Greeting {
    private val platform = getPlatform()

    fun greet(): String = "Hello, ${platform.name}!"
}
