package com.coconutshell.activitytracker

import android.content.Context
import com.coconutshell.activitytracker.data.local.ActivityDatabase
import com.coconutshell.activitytracker.data.repository.*

class AppContainer(context: Context) {
    private val db = ActivityDatabase.create(context)
    val things: ThingRepository = ThingRepositoryImpl(db.thingDao())
    val occurrences: OccurrenceRepository = OccurrenceRepositoryImpl(db.occurrenceDao())
    val libraries: LibraryRepository = LibraryRepositoryImpl(db.libraryDao())
}
