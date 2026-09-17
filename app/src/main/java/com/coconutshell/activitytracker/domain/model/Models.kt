package com.coconutshell.activitytracker.domain.model

data class Thing(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class Occurrence(
    val id: Long,
    val thingId: Long,
    val occurredAt: Long,
    val quantity: Double
)

data class Library(
    val id: Long,
    val name: String
)
