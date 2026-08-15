package com.inventoria.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName

/**
 * A user-managed activity label sitting one level above Task.name -- the missing middle layer
 * between a free-text name and a TaskKind.
 *
 * The same activity can be worth different amounts depending on circumstance: "Eating with V"
 * (home-made, Blueberry +1) and "Eating at a restaurant" (takeout, Tangerine -1) are the same
 * *type* of thing while carrying different names and different kinds. The Kind stays attached to
 * the individual Task; the type is what ties them together for autofill and reporting.
 *
 * Tasks reference these by id rather than by name, so renaming a type propagates to all history
 * and soft-deleting one leaves old tasks still resolvable.
 */
@Entity
data class TaskType(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted") var isDeleted: Boolean = false,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
    @get:Exclude @set:Exclude var isDirty: Boolean = false
)

/**
 * Seeded once per account. Ids are deterministic rather than random UUIDs so that two devices on
 * the same account generate identical rows -- a double-seed then collapses into an idempotent
 * REPLACE instead of producing sixteen duplicates. See TaskTypeRepository.seedDefaultsIfNeeded.
 *
 * All of these are renameable and deletable, "Other" included -- it is just the seeded catch-all
 * for when nothing else fits, not a protected row.
 */
val DEFAULT_TASK_TYPE_NAMES = listOf(
    "Eating",
    "Sleep",
    "Work",
    "Study",
    "Exercise",
    "Commute",
    "Chores",
    "Errands",
    "Social",
    "Family",
    "Screen Time",
    "Self-Care",
    "Admin",
    "Hobby",
    "Health",
    "Other"
)

/** "Screen Time" -> "type_screen_time". Stable across devices and app versions. */
fun defaultTaskTypeId(name: String): String =
    "type_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
