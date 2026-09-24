package com.inventoria.shared.model

import kotlin.time.Clock

/** Wall-clock millis, the unit every synced timestamp is stored in. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
