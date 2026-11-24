package com.focusforge.app.data.repository

import com.focusforge.app.data.local.dao.SessionDao
import com.focusforge.app.data.local.entity.SessionEntity
import com.focusforge.app.data.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

interface SessionRepository {
    fun getAllSessions(): Flow<List<Session>>
    fun getTodaySessions(): Flow<List<Session>>
    fun getTodayTotalMinutes(): Flow<Int>
    fun getTodayDeepMinutes(): Flow<Int>
    fun getTotalDeepWorkMinutesSince(since: Long): Flow<Int>
    suspend fun saveSession(session: Session): Long
    suspend fun deleteSession(session: Session)
}

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    override fun getAllSessions(): Flow<List<Session>> {
        return sessionDao.getAllSessions().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTodaySessions(): Flow<List<Session>> {
        val (startOfDay, endOfDay) = getTodayTimeBounds()
        return sessionDao.getSessionsForDay(startOfDay, endOfDay).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTodayTotalMinutes(): Flow<Int> {
        val (startOfDay, endOfDay) = getTodayTimeBounds()
        return sessionDao.totalWorkMinutesForDay(startOfDay, endOfDay).map { it ?: 0 }
    }

    override fun getTodayDeepMinutes(): Flow<Int> {
        val (startOfDay, endOfDay) = getTodayTimeBounds()
        return sessionDao.deepWorkMinutesForDay(startOfDay, endOfDay).map { it ?: 0 }
    }

    override fun getTotalDeepWorkMinutesSince(since: Long): Flow<Int> {
        return sessionDao.totalDeepWorkMinutesSince(since).map { it ?: 0 }
    }

    override suspend fun saveSession(session: Session): Long {
        return sessionDao.insert(SessionEntity.fromDomain(session))
    }

    override suspend fun deleteSession(session: Session) {
        sessionDao.delete(SessionEntity.fromDomain(session))
    }

    private fun getTodayTimeBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis
        return Pair(startOfDay, endOfDay)
    }
}
