package io.disruption.focal.core.model

/** How hard it is to end a Focus Session before its planned end. Declared from least to most strict. */
enum class Strictness {
    Normal,
    Delayed,
    Locked;

    fun isStricterThan(other: Strictness): Boolean = ordinal > other.ordinal
}
