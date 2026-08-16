package com.inventoria.app.data.repository

import android.content.Context
import android.util.Log
import com.inventoria.app.data.local.InventoryDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erases everything this device holds on its own: the Room database, every stored preference, and
 * the camera's scratch files.
 *
 * Deleting an account used to mean only [FirebaseAuthRepository.deleteUserAccount], and every one
 * of the three things it removes is remote -- the Realtime Database node, the Storage objects and
 * the Auth record. Room and DataStore survived it untouched, so the button labelled "Wipe Local
 * Account Data" left every item, task and todo exactly where it was.
 */
@Singleton
class LocalDataRepository @Inject constructor(
    private val database: InventoryDatabase,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    private val TAG = "LocalDataRepository"

    /**
     * Callers must stop sync first ([FirebaseSyncRepository.stopSync]) -- a live listener holding
     * the outgoing account's node would otherwise be racing this.
     */
    suspend fun wipeAllLocalData() {
        withContext(Dispatchers.IO) {
            // Truncates every table but keeps the schema, so the singleton database instance the
            // rest of the app is already holding stays usable and its flows just re-emit empty. It
            // blocks and runs its own transaction, hence the IO dispatcher.
            database.clearAllTables()

            settingsRepository.clearAll()

            // Camera captures land here before being attached to an item (see AddEditItemViewModel);
            // whatever is left over belongs to the account being deleted.
            val tempImages = File(context.cacheDir, "temp_images")
            if (!tempImages.deleteRecursively()) {
                Log.w(TAG, "Some temp camera files under ${tempImages.path} could not be deleted")
            }

            Log.d(TAG, "Local data wiped")
        }
    }
}
