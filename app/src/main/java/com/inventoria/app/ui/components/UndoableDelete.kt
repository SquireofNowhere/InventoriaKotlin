package com.inventoria.app.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The "Deleted X — Undo" snackbar, held on the ViewModel side.
 *
 * Every delete in this app is already a tombstone that survives for
 * [com.inventoria.app.data.DELETED_ROW_RETENTION_MILLIS], so the data to undo with has always been
 * there; what was missing was any way to ask for it back. This is the cheap half of that -- it
 * catches the mistake you notice immediately, which is most of them.
 *
 * Deliberately holds exactly one action. A queue of pending undos would mean a snackbar promising to
 * restore something two deletes ago, which is a worse offer than none: the label would no longer
 * match what the button does.
 */
class UndoableDeleteController {

    private var pendingRestore: (suspend () -> Unit)? = null

    private val _prompts = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the label of something just deleted, e.g. "Buy flour". */
    val prompts: SharedFlow<String> = _prompts.asSharedFlow()

    /** Records [restore] as undoable and asks the UI to offer it. Replaces any earlier offer. */
    fun offer(label: String, restore: suspend () -> Unit) {
        pendingRestore = restore
        _prompts.tryEmit(label)
    }

    /**
     * Runs the pending restore, if the offer is still open.
     *
     * Clearing first means a double tap on Undo cannot restore twice -- harmless for a single row
     * (the second write is a no-op) but not for a session restore, which would issue a second
     * timestamp bump for no reason.
     */
    suspend fun undo() {
        val restore = pendingRestore ?: return
        pendingRestore = null
        restore()
    }

    /** Drops the offer without running it -- e.g. once the same thing has been deleted for good. */
    fun clear() {
        pendingRestore = null
    }
}
