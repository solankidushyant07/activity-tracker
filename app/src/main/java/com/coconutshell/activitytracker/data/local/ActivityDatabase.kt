package com.coconutshell.activitytracker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "things")
data class ThingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "occurrences",
    foreignKeys = [ForeignKey(
        entity = ThingEntity::class,
        parentColumns = ["id"],
        childColumns = ["thingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("thingId"), Index("occurredAt")]
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val thingId: Long,
    val occurredAt: Long,
    val quantity: Double
)

@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "thing_library",
    primaryKeys = ["thingId", "libraryId"],
    foreignKeys = [
        ForeignKey(entity = ThingEntity::class, parentColumns = ["id"], childColumns = ["thingId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LibraryEntity::class, parentColumns = ["id"], childColumns = ["libraryId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("libraryId")]
)
data class ThingLibraryCrossRef(
    val thingId: Long,
    val libraryId: Long
)

@Dao
interface ThingDao {
    @Query("SELECT * FROM things ORDER BY updatedAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ThingEntity>>

    @Query("SELECT * FROM things WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun search(query: String): Flow<List<ThingEntity>>

    @Query("SELECT * FROM things WHERE id = :id LIMIT 1")
    fun observe(id: Long): Flow<ThingEntity?>

    @Insert
    suspend fun insert(entity: ThingEntity): Long

    @Update
    suspend fun update(entity: ThingEntity)

    @Delete
    suspend fun delete(entity: ThingEntity)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrences WHERE thingId = :thingId ORDER BY occurredAt DESC")
    fun observeForThing(thingId: Long): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrences WHERE thingId = :thingId AND occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt DESC")
    suspend fun between(thingId: Long, start: Long, end: Long): List<OccurrenceEntity>

    @Insert
    suspend fun insert(entity: OccurrenceEntity): Long

    @Update
    suspend fun update(entity: OccurrenceEntity)

    @Delete
    suspend fun delete(entity: OccurrenceEntity)

    @Query("DELETE FROM occurrences WHERE thingId = :thingId")
    suspend fun deleteForThing(thingId: Long)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM libraries ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<LibraryEntity>>

    @Insert
    suspend fun insert(entity: LibraryEntity): Long

    @Update
    suspend fun update(entity: LibraryEntity)

    @Delete
    suspend fun delete(entity: LibraryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCrossRef(ref: ThingLibraryCrossRef)

    @Delete
    suspend fun removeCrossRef(ref: ThingLibraryCrossRef)

    @Query("SELECT libraryId FROM thing_library WHERE thingId = :thingId")
    fun observeLibraryIds(thingId: Long): Flow<List<Long>>

    @Query("SELECT thingId FROM thing_library WHERE libraryId = :libraryId")
    fun observeThingIds(libraryId: Long): Flow<List<Long>>
}

@Database(
    entities = [ThingEntity::class, OccurrenceEntity::class, LibraryEntity::class, ThingLibraryCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class ActivityDatabase : RoomDatabase() {
    abstract fun thingDao(): ThingDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: android.content.Context): ActivityDatabase =
            Room.databaseBuilder(
                context,
                ActivityDatabase::class.java,
                "activity_tracker.db"
            ).build()
    }
}
