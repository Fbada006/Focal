package io.disruption.focal.core.model

/** Every Focus Session ends with exactly one outcome. */
enum class Outcome {
    /** Ran to its planned end, whatever its Strictness. */
    Completed,

    /** A Normal or Delayed session the user ended before its planned end. */
    EndedEarly,

    /** A Locked session whose blocking was defeated from outside Focal before it ran out. */
    Broken,
}
