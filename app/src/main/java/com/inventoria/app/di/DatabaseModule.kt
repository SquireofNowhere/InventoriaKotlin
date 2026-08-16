package com.inventoria.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inventoria.app.data.local.CollectionDao
import com.inventoria.app.data.local.InventoryDao
import com.inventoria.app.data.local.InventoryDatabase
import com.inventoria.app.data.local.ItemLinkDao
import com.inventoria.app.data.local.TaskDao
import com.inventoria.app.data.local.TaskTypeDao
import com.inventoria.app.data.local.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * The versions a destructive rebuild is still allowed from.
     *
     * Only 12, 13 and 14 have a complete migration path up to the current version. Everything below
     * that predates the migrations entirely -- 4→5 through 11→12 were never written -- so a
     * database at any of those versions cannot be brought forward and has to be recreated.
     *
     * Naming them explicitly, rather than allowing a blanket fallback, is the whole point: a
     * missing migration from a *future* version is then a loud crash at startup instead of a silent
     * wipe. Losing local data is recoverable for a signed-in user (it re-pulls from Firebase) but
     * total for a local-only one, which is far too quiet a failure to leave as the default for
     * mistakes not yet made.
     *
     * INVARIANT: no version listed here may also be the start OR end version of anything passed to
     * addMigrations() below. Room rejects that combination outright at build() -- not lazily on a
     * database that actually needs it, but for every user on every launch. A MIGRATION_3_4 used to
     * sit here alongside 3 in this list, and the resulting IllegalArgumentException took down the
     * whole process at startup: the first thing to ask for the database is SyncWorker, on a
     * WorkManager thread, and InventoriaApplication's global handler turns any uncaught throwable
     * into System.exit(1). So the app died behind the splash screen with nothing on screen to say
     * why. Adding a real migration for one of these versions means removing it from this list in
     * the same edit -- and, since 4→5 through 11→12 do not exist, filling in the whole chain.
     */
    private val LEGACY_UNMIGRATABLE_VERSIONS = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

    /**
     * Task Types (v13). Purely additive, so it gets a real migration rather than being allowed to
     * fall through to a destructive rebuild -- which wipes the local database, recoverably for a
     * signed-in user and totally for a local-only one.
     */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS TaskType (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL,
                    isDirty INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE Task ADD COLUMN taskTypeId TEXT")
        }
    }

    /** Optional time-of-day on todo deadlines (v14). Additive, same reasoning as v13 above. */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Todo ADD COLUMN deadlineMinuteOfDay INTEGER")
        }
    }

    /** Task Type on Todos (v15). Additive, same reasoning as v13 above. */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE Todo ADD COLUMN taskTypeId TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideInventoryDatabase(
        @ApplicationContext context: Context
    ): InventoryDatabase {
        return Room.databaseBuilder(
            context,
            InventoryDatabase::class.java,
            InventoryDatabase.DATABASE_NAME
        )
            // Every version here must be absent from LEGACY_UNMIGRATABLE_VERSIONS -- see its KDoc.
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            // Scoped to the versions listed above instead of a blanket fallback. Bump the database
            // version without writing the migration and this now throws
            // IllegalStateException("A migration from 15 to 16 was required but not found") on
            // first launch -- which is the correct outcome, because the alternative is every user
            // silently losing their local data and nobody finding out until someone notices their
            // todos are gone.
            .fallbackToDestructiveMigrationFrom(*LEGACY_UNMIGRATABLE_VERSIONS)
            // Installing an older build over a newer one would otherwise crash outright. A
            // downgrade only happens while developing, and recreating is the only sane response.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideInventoryDao(database: InventoryDatabase): InventoryDao {
        return database.inventoryDao()
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: InventoryDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideCollectionDao(database: InventoryDatabase): CollectionDao {
        return database.collectionDao()
    }

    @Provides
    @Singleton
    fun provideItemLinkDao(database: InventoryDatabase): ItemLinkDao {
        return database.itemLinkDao()
    }

    @Provides
    @Singleton
    fun provideTodoDao(database: InventoryDatabase): TodoDao {
        return database.todoDao()
    }

    @Provides
    @Singleton
    fun provideTaskTypeDao(database: InventoryDatabase): TaskTypeDao {
        return database.taskTypeDao()
    }
}
