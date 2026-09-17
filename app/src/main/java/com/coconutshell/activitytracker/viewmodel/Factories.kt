package com.coconutshell.activitytracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.coconutshell.activitytracker.data.repository.*
import com.coconutshell.activitytracker.reminder.ReminderScheduler
import com.coconutshell.activitytracker.settings.SettingsStore

class HomeFactory(private val things: ThingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(things) as T
}

class ThingDetailsFactory(
    private val things: ThingRepository,
    private val occurrences: OccurrenceRepository,
    private val libraries: LibraryRepository,
    private val id: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ThingDetailsViewModel(things, occurrences, libraries, id) as T
}

class SettingsFactory(
    private val store: SettingsStore,
    private val scheduler: ReminderScheduler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(store, scheduler) as T
}
