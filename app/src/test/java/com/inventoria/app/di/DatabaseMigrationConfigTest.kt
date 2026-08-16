package com.inventoria.app.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The migration configuration's own invariants, checked on the JVM in milliseconds.
 *
 * These are the mistakes that do not show up as a failing migration but as the app dying at
 * startup, or as a database that silently cannot be brought forward. Both have happened here: a
 * MIGRATION_3_4 once sat in [DatabaseModule.ALL_MIGRATIONS] while 3 was also in
 * [DatabaseModule.LEGACY_UNMIGRATABLE_VERSIONS], Room rejected that combination at `build()` for
 * every user on every launch, and because the first thing to ask for the database is SyncWorker on
 * a WorkManager thread, the process exited behind the splash screen with nothing on screen to say
 * why.
 *
 * The rest of the migration coverage needs a device -- see InventoryDatabaseMigrationTest.
 */
class DatabaseMigrationConfigTest {

    private val legacyVersions = DatabaseModule.LEGACY_UNMIGRATABLE_VERSIONS.toSet()
    private val migrations = DatabaseModule.ALL_MIGRATIONS

    /** Room rejects a version that is both destructively rebuildable and migratable. */
    @Test
    fun `no migration starts or ends on a version marked unmigratable`() {
        migrations.forEach { migration ->
            assertFalse(
                "Migration ${migration.startVersion} -> ${migration.endVersion} starts on " +
                    "${migration.startVersion}, which is also in LEGACY_UNMIGRATABLE_VERSIONS. " +
                    "Room rejects that at build() and the app dies at startup. Remove it from " +
                    "that list in the same edit.",
                migration.startVersion in legacyVersions
            )
            assertFalse(
                "Migration ${migration.startVersion} -> ${migration.endVersion} ends on " +
                    "${migration.endVersion}, which is also in LEGACY_UNMIGRATABLE_VERSIONS. " +
                    "Room rejects that at build() and the app dies at startup. Remove it from " +
                    "that list in the same edit.",
                migration.endVersion in legacyVersions
            )
        }
    }

    /** A gap would strand a database: too new to rebuild destructively, too old to migrate. */
    @Test
    fun `migrations form an unbroken chain`() {
        val ordered = migrations.sortedBy { it.startVersion }
        ordered.zipWithNext { current, next ->
            assertEquals(
                "Gap between migrations: ${current.startVersion} -> ${current.endVersion} is " +
                    "followed by ${next.startVersion} -> ${next.endVersion}. A database sitting " +
                    "on ${current.endVersion} would have no way forward.",
                current.endVersion,
                next.startVersion
            )
        }
    }

    /** And the chain has to begin exactly where the destructive rebuild stops covering things. */
    @Test
    fun `the chain starts immediately above the last unmigratable version`() {
        assertEquals(
            "The lowest migration must start one version above the highest entry in " +
                "LEGACY_UNMIGRATABLE_VERSIONS, or a database on the version in between can " +
                "neither be rebuilt nor migrated.",
            legacyVersions.max() + 1,
            migrations.minOf { it.startVersion }
        )
    }
}
