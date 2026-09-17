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

class SettingsViewModel : ViewModel() {
    val morningEnabled = MutableStateFlow(false)
    val nightEnabled = MutableStateFlow(false)
    val morningTime = MutableStateFlow("08:00")
    val nightTime = MutableStateFlow("21:00")
    fun toggleMorning(v: Boolean) { morningEnabled.value = v }
    fun toggleNight(v: Boolean) { nightEnabled.value = v }
    fun setMorning(v: String) { morningTime.value = v }
    fun setNight(v: String) { nightTime.value = v }
}
