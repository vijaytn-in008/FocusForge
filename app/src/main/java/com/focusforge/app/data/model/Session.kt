package com.focusforge.app.data.model

/**
 * Domain model representing a completed study / focus session.
 */
data class Session(
    val id: Long = 0,
    val subject: String,
    val topic: String,
    val startTime: Long,       // Epoch milliseconds
    val durationMin: Int,      // Duration in whole minutes
    val focusRating: Int,      // Self-assessed quality 1..5
    val type: SessionType
)
