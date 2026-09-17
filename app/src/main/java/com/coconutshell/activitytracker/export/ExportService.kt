package com.coconutshell.activitytracker.export

import android.content.Context
import com.coconutshell.activitytracker.data.repository.*
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first

class ExportService(
    private val context: Context,
    private val things: ThingRepository,
    private val occurrences: OccurrenceRepository
) {
    private val date = DateTimeFormatter.ofPattern("d MMMM yyyy")
    private val time = DateTimeFormatter.ofPattern("h:mm a")
    private val zone get() = ZoneId.systemDefault()

    suspend fun export(): File {
        val folder = File(context.cacheDir, "activity-export").apply { deleteRecursively(); mkdirs() }
        val used = mutableSetOf<String>()
        things.observeThings().first().forEach { thing ->
            val rows = occurrences.observeForThing(thing.id).first().sortedByDescending { it.occurredAt }
            val base = thing.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "Thing" }
            var filename = base
            var suffix = 2
            while (!used.add(filename.lowercase())) filename = "$base-${suffix++}"
            val md = buildString {
                append("# ${thing.name}\n\n")
                var currentDate = ""
                rows.forEach { item ->
                    val z = Instant.ofEpochMilli(item.occurredAt).atZone(zone)
                    val d = z.format(date)
                    if (d != currentDate) {
                        currentDate = d
                        append("## $d\n\n")
                    }
                    append("- ${z.format(time)}")
                    if (item.quantity != 1.0) append(" · quantity ${item.quantity}")
                    append('\n')
                }
                if (rows.isEmpty()) append("_No recorded occurrences._\n")
            }
            File(folder, "$filename.md").writeText(md)
        }
        val zip = File(context.cacheDir, "ActivityTracker.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            folder.listFiles()?.sortedBy { it.name }?.forEach { file ->
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }
}
