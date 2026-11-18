package io.disruption.focal.ui

import io.disruption.focal.core.sayHello

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return sayHello(platform.name)
    }
}