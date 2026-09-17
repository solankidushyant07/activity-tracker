package com.coconutshell.activitytracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coconutshell.activitytracker.data.repository.*
import com.coconutshell.activitytracker.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val things: ThingRepository,
    private val occurrences: OccurrenceRepository,
    private val libraries: LibraryRepository
) : ViewModel() {
    val query = MutableStateFlow("")
    val selectedLibrary = MutableStateFlow<Long?>(null)

    val librariesState = libraries.observeLibraries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val thingsState: StateFlow<List<Thing>> = combine(
        query,
        selectedLibrary
    ) { q, libraryId -> q.trim() to libraryId }
        .flatMapLatest { (q, libraryId) ->
            val base = if (q.isBlank()) {
                things.observeThings()
            } else {
                things.search(q)
            }
            if (libraryId == null) {
                base
            } else {
                combine(base, libraries.observeThingIds(libraryId)) { rows, ids ->
                    rows.filter { it.id in ids }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setLibrary(libraryId: Long?) {
        selectedLibrary.value = libraryId
    }

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) onCreated(things.create(trimmed))
        }
    }

    suspend fun record(thingId: Long, at: Long, quantity: Double) {
        require(quantity > 0.0 && quantity.isFinite()) {
            "Quantity must be greater than zero."
        }
        occurrences.record(thingId, at, quantity)
    }
}

class ThingDetailsViewModel(
    private val things: ThingRepository,
    private val occurrences: OccurrenceRepository,
    private val libraries: LibraryRepository,
    private val id: Long
) : ViewModel() {
    val thing = things.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history = occurrences.observeForThing(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryIds = libraries.observeLibraryIds(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allLibraries = libraries.observeLibraries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun record(at: Long, quantity: Double) {
        require(quantity > 0.0 && quantity.isFinite()) { "Quantity must be greater than zero." }
        occurrences.record(id, at, quantity)
    }

    suspend fun updateOccurrence(item: Occurrence) {
        require(item.quantity > 0.0 && item.quantity.isFinite()) {
            "Quantity must be greater than zero."
        }
        occurrences.update(item)
    }

    suspend fun deleteOccurrence(item: Occurrence) {
        occurrences.delete(item)
    }

    suspend fun toggleLibrary(libraryId: Long, selected: Boolean) {
        if (selected) {
            libraries.addThing(id, libraryId)
        } else {
            libraries.removeThing(id, libraryId)
        }
    }

    suspend fun updateThing(name: String) {
        val current = thing.value ?: return
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Thing name cannot be empty." }
        things.update(current.copy(name = trimmed))
    }

    suspend fun deleteThing() {
        thing.value?.let { things.delete(it) }
    }
}

class SettingsViewModel(
    private val store: com.coconutshell.activitytracker.settings.SettingsStore,
    private val scheduler: com.coconutshell.activitytracker.reminder.ReminderScheduler
) : ViewModel() {
    val settings = store.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.coconutshell.activitytracker.settings.ReminderSettings()
    )
    val error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            store.settings.collect { current ->
                error.value = null
                runCatching {
                    scheduler.setMorning(
                        current.morningEnabled,
                        current.morningTime.hour(),
                        current.morningTime.minute()
                    )
                    scheduler.setNight(
                        current.nightEnabled,
                        current.nightTime.hour(),
                        current.nightTime.minute()
                    )
                }.onFailure {
                    error.value = "Couldn't schedule the reminder. Check your device reminder permissions."
                }
            }
        }
    }

    fun toggleMorning(value: Boolean) = viewModelScope.launch {
        store.setMorningEnabled(value)
    }

    fun toggleNight(value: Boolean) = viewModelScope.launch {
        store.setNightEnabled(value)
    }

    fun setMorning(value: String) = viewModelScope.launch {
        store.setMorningTime(value)
    }

    fun setNight(value: String) = viewModelScope.launch {
        store.setNightTime(value)
    }

    private fun String.hour(): Int =
        substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 8

    private fun String.minute(): Int =
        substringAfter(':', "0").toIntOrNull()?.coerceIn(0, 59) ?: 0
}
