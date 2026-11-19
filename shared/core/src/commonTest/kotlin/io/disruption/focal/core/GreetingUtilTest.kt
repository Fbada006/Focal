package io.disruption.focal.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingUtilTest {

    @Test
    fun sayHelloGreetsByName() {
        assertEquals("Hello, Focal!", sayHello("Focal"))
    }
}
