package io.disruption.focal.data

import io.disruption.focal.data.db.FocalDatabase

/** A fresh in-memory [FocalDatabase] for each test. */
expect fun inMemoryDatabase(): FocalDatabase
