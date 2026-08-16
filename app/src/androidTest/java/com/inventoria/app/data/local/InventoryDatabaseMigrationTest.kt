package com.inventoria.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inventoria.app.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Migrations from the earliest version there is an exported schema for.
 *
 * That is 15: `exportSchema` was turned on at that version, so 12, 13 and 14 have migrations but
 * nothing to check them against, and [MigrationTestHelper.createDatabase] cannot build a starting
 * database without the schema JSON. 15 -> 16 is the first bump these tests can actually catch a
 * mistake in -- which is the point of writing them before that bump exists rather than after.
 *
 * Neither test names a target version. Both hand the database to Room's own builder and let it
 * migrate as far as [InventoryDatabase]'s `@Database(version = ...)` says, so adding a migration is
 * the only thing needed to bring it under test.
 */
@RunWith(AndroidJUnit4::class)
class InventoryDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InventoryDatabase::class.java
    )

    /**
     * Every migration runs, and Room validates the result against the schema it expects.
     *
     * This is what fails when a migration's SQL disagrees with the entity it is supposed to
     * produce -- a missed column, a wrong affinity, a forgotten index. Previously that surfaced
     * only as a crash on a real device, on the first launch after an update.
     */
    @Test
    @Throws(IOException::class)
    fun migratesFromEarliestExportedSchemaToLatest() {
        helper.createDatabase(TEST_DB, EARLIEST_EXPORTED_VERSION).close()

        // Room runs the remaining migrations on open and validates the final schema itself, so
        // this needs no target version and picks up future ones for free.
        openWithRealMigrations().close()
    }

    /**
     * Rows written at [EARLIEST_EXPORTED_VERSION] are still there, and still readable through the
     * DAOs, afterwards.
     *
     * Schema validation alone would pass a migration that recreated a table and dropped everything
     * in it, which for a local-only account is unrecoverable -- see the reasoning on
     * DatabaseModule.LEGACY_UNMIGRATABLE_VERSIONS.
     */
    @Test
    @Throws(IOException::class)
    fun dataSurvivesMigrationFromEarliestExportedSchema() {
        helper.createDatabase(TEST_DB, EARLIEST_EXPORTED_VERSION).use { db ->
            db.execSQL(
                """
                INSERT INTO TaskType (id, name, isDeleted, updatedAt, isDirty)
                VALUES ('type-1', 'Cooking', 0, 1000, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO Todo (
                    id, title, kind, taskTypeId, deadline, deadlineMinuteOfDay, priority,
                    parentTodoId, state, completedAt, createdAt, activeSessionGroupId,
                    isDeleted, updatedAt, isDirty
                ) VALUES (
                    'todo-1', 'Buy flour', 'BLUEBERRY', 'type-1', NULL, NULL, 'B1',
                    NULL, 'INCOMPLETE', NULL, 2000, NULL,
                    0, 2000, 0
                )
                """.trimIndent()
            )
        }

        val db = openWithRealMigrations()
        try {
            runBlocking {
                val taskType = db.taskTypeDao().getTaskTypeById("type-1")
                assertEquals("Cooking", taskType?.name)

                val todo = db.todoDao().getTodoById("todo-1")
                assertEquals("Buy flour", todo?.title)
                assertEquals("type-1", todo?.taskTypeId)
                // Added by MIGRATION_13_14 and nullable, so it must come through unset rather than
                // defaulted to something the user never chose.
                assertNull(todo?.deadlineMinuteOfDay)
            }
        } finally {
            db.close()
        }
    }

    /**
     * Opens the test database through Room with the same migrations the app ships
     * ([DatabaseModule.ALL_MIGRATIONS]) -- referencing that array rather than restating it is what
     * stops a migration being added to the app and not to this test.
     */
    private fun openWithRealMigrations(): InventoryDatabase =
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            InventoryDatabase::class.java,
            TEST_DB
        )
            .addMigrations(*DatabaseModule.ALL_MIGRATIONS)
            .build()
            .also { helper.closeWhenFinished(it) }

    companion object {
        private const val TEST_DB = "migration-test"

        /** The lowest version in app/schemas/. See the class KDoc for why it is not 1. */
        private const val EARLIEST_EXPORTED_VERSION = 15
    }
}
