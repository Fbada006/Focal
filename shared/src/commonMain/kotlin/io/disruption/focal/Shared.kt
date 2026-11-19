package io.disruption.focal

/**
 * The umbrella module has almost no code of its own: it re-exports `core`, `data` and `ui`.
 * Entry points that wire the layers together for each app (such as starting Koin) live here.
 */
internal object Shared
