package com.coconutshell.activitytracker.data.repository

import com.coconutshell.activitytracker.data.local.*
import com.coconutshell.activitytracker.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ThingRepository {
    fun observeThings(): Flow<List<Thing>>
    fun search(query: String): Flow<List<Thing>>
    fun observe(id: Long): Flow<Thing?>
    suspend fun create(name: String): Long
    suspend fun update(thing: Thing)
    suspend fun delete(thing: Thing)
}

interface OccurrenceRepository {
    fun observeForThing(thingId: Long): Flow<List<Occurrence>>
    suspend fun record(thingId: Long, occurredAt: Long, quantity: Double): Long
    suspend fun update(occurrence: Occurrence)
    suspend fun delete(occurrence: Occurrence)
    suspend fun between(thingId: Long, start: Long, end: Long): List<Occurrence>
}

interface LibraryRepository {
    fun observeLibraries(): Flow<List<Library>>
    fun observeLibraryIds(thingId: Long): Flow<List<Long>>
    fun observeThingIds(libraryId: Long): Flow<List<Long>>
    suspend fun create(name: String): Long
    suspend fun rename(library: Library)
    suspend fun delete(library: Library)
    suspend fun addThing(thingId: Long, libraryId: Long)
    suspend fun removeThing(thingId: Long, libraryId: Long)
}

class ThingRepositoryImpl(private val dao: ThingDao) : ThingRepository {
    override fun observeThings() = dao.observeAll().map { list -> list.map { Thing(it.id, it.name, it.createdAt, it.updatedAt) } }
    override fun search(query: String) = dao.search(query).map { list -> list.map { Thing(it.id, it.name, it.createdAt, it.updatedAt) } }
    override fun observe(id: Long) = dao.observe(id).map { it?.let { e -> Thing(e.id, e.name, e.createdAt, e.updatedAt) } }
    override suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(ThingEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }
    override suspend fun update(thing: Thing) = dao.update(ThingEntity(thing.id, thing.name.trim(), thing.createdAt, System.currentTimeMillis()))
    override suspend fun delete(thing: Thing) = dao.delete(ThingEntity(thing.id, thing.name, thing.createdAt, thing.updatedAt))
}

class OccurrenceRepositoryImpl(private val dao: OccurrenceDao) : OccurrenceRepository {
    override fun observeForThing(thingId: Long) = dao.observeForThing(thingId).map { list -> list.map { Occurrence(it.id, it.thingId, it.occurredAt, it.quantity) } }
    override suspend fun record(thingId: Long, occurredAt: Long, quantity: Double) =
        dao.insert(OccurrenceEntity(thingId = thingId, occurredAt = occurredAt, quantity = quantity))
    override suspend fun update(occurrence: Occurrence) =
        dao.update(OccurrenceEntity(occurrence.id, occurrence.thingId, occurrence.occurredAt, occurrence.quantity))
    override suspend fun delete(occurrence: Occurrence) =
        dao.delete(OccurrenceEntity(occurrence.id, occurrence.thingId, occurrence.occurredAt, occurrence.quantity))
    override suspend fun between(thingId: Long, start: Long, end: Long) =
        dao.between(thingId, start, end).map { Occurrence(it.id, it.thingId, it.occurredAt, it.quantity) }
}

class LibraryRepositoryImpl(private val dao: LibraryDao) : LibraryRepository {
    override fun observeLibraries() = dao.observeAll().map { list -> list.map { Library(it.id, it.name) } }
    override fun observeLibraryIds(thingId: Long) = dao.observeLibraryIds(thingId)
    override fun observeThingIds(libraryId: Long) = dao.observeThingIds(libraryId)
    override suspend fun create(name: String) = dao.insert(LibraryEntity(name = name.trim()))
    override suspend fun rename(library: Library) = dao.update(LibraryEntity(library.id, library.name.trim()))
    override suspend fun delete(library: Library) = dao.delete(LibraryEntity(library.id, library.name))
    override suspend fun addThing(thingId: Long, libraryId: Long) = dao.addCrossRef(ThingLibraryCrossRef(thingId, libraryId))
    override suspend fun removeThing(thingId: Long, libraryId: Long) = dao.removeCrossRef(ThingLibraryCrossRef(thingId, libraryId))
}
