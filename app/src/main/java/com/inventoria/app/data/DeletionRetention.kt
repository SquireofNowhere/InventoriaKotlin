package com.inventoria.app.data

import java.util.concurrent.TimeUnit

/**
 * How long a soft-deleted row stays restorable before it is purged for good.
 *
 * Every delete in the app is a tombstone -- `isDeleted = 1`, synced like any other change -- and
 * this is the only thing that ever removes one. It was 24 hours, written out as a bare
 * `86_400_000` at four separate call sites, which made it look like an implementation detail of
 * each cleanup loop rather than the app's single answer to "how long is a delete undoable".
 *
 * Lengthening it is safe in both directions that matter. A tombstone has to outlive any stale
 * *non*-deleted copy of the same row still sitting on another device, or that device wins the
 * last-write-wins merge and resurrects it -- so a longer window is strictly more convergence-safe,
 * never less. And the rows are tiny.
 */
val DELETED_ROW_RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

/** Cut-off to hand the `purgeOldDeleted*` queries: anything tombstoned before this is expendable. */
fun deletedRowPurgeThreshold(now: Long = System.currentTimeMillis()): Long =
    now - DELETED_ROW_RETENTION_MILLIS
