package com.coconutshell.activitytracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coconutshell.activitytracker.data.repository.*
import com.coconutshell.activitytracker.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val things: ThingRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val thingsState: StateFlow<List<Thing>> = query
        .debounce(120)
        .flatMapLatest { q -> if (q.isBlank()) things.observeThings() else things.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) { query.value = value }
    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            if (name.isNotBlank()) onCreated(things.create(name))
        }
    }
}

class ThingDetailsViewModel(
    private val things: ThingRepository,
    private val occurrences: OccurrenceRepository,
    private val libraries: LibraryRepository,
    private val id: Long
) : ViewModel() {
    val thing = things.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = occurrences.observeForThing(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val libraryIds = libraries.observeLibraryIds(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allLibraries = libraries.observeLibraries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun record(at: Long, quantity: Double, onDone: () -> Unit) = viewModelScope.launch {
        occurrences.record(id, at, quantity)
        onDone()
    }
    fun updateOccurrence(item: Occurrence) = viewModelScope.launch { occurrences.update(item) }
    fun deleteOccurrence(item: Occurrence) = viewModelScope.launch { occurrences.delete(item) }
    fun toggleLibrary(libraryId: Long, selected: Boolean) = viewModelScope.launch {
        if (selected) libraries.addThing(id, libraryId) else libraries.removeThing(id, libraryId)
    }
    fun deleteThing(onDeleted: () -> Unit) = viewModelScope.launch {
        thing.value?.let { things.delete(it) }
        onDeleted()
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

    init {
        viewModelScope.launch {
            store.settings.collect { current ->
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
