package com.inventoria.app.data.repository

/**
 * An invite code and the moment it stops being usable.
 *
 * `invites/{code}` used to be a bare string holding the owner's uid, which made a code valid
 * forever -- a leaked one could never be taken back except by retiring it by hand, and a
 * brute-force search had every code ever issued to aim at rather than only those live right now.
 * It is now `{ uid, expiresAt }`, and both the client and the database rules check the expiry.
 *
 * Expiry gates *joining*, not access. Someone who already linked stays linked after the code dies,
 * because their `sharedWith` entry is what grants access from then on; cutting them off is what
 * Revoke is for.
 */
data class InviteCode(
    val code: String,
    /** Epoch millis. Zero for a legacy string-shaped entry, which reads as long expired. */
    val expiresAt: Long
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt <= now
}
