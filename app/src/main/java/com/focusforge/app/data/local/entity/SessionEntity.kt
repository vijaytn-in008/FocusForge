package com.focusforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType

/**
 * Room entity representing a saved study or deep work session.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val topic: String,
    val startTime: Long,
    val durationMin: Int,
    val focusRating: Int,
    val type: SessionType
) {
    fun toDomain(): Session = Session(
        id = id,
        subject = subject,
        topic = topic,
        startTime = startTime,
        durationMin = durationMin,
        focusRating = focusRating,
        type = type
    )

    companion object {
        fun fromDomain(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            subject = session.subject,
            topic = session.topic,
            startTime = session.startTime,
            durationMin = session.durationMin,
            focusRating = session.focusRating,
            type = session.type
        )
    }
}
