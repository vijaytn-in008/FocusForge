package com.focusforge.app.data.repository

import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.local.dao.EmergencyBypassLogDao
import com.focusforge.app.data.local.entity.BlockedAppEntity
import com.focusforge.app.data.local.entity.EmergencyBypassLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface BlockedAppRepository {
    fun getAllApps(): Flow<List<BlockedAppEntity>>
    fun getActiveBlockedApps(): Flow<List<BlockedAppEntity>>
    suspend fun getActiveBlockedAppsSync(): List<BlockedAppEntity>
    fun getBlockedPackageNames(): Flow<List<String>>
    suspend fun setAppBlocked(packageName: String, appName: String, isBlocked: Boolean)
    suspend fun removeApp(packageName: String)
    suspend fun logBypass(durationSeconds: Int = 60, reason: String = ""): Long
    fun getBypassLogs(): Flow<List<EmergencyBypassLogEntity>>
}

@Singleton
class BlockedAppRepositoryImpl @Inject constructor(
    private val blockedAppDao: BlockedAppDao,
    private val emergencyBypassLogDao: EmergencyBypassLogDao
) : BlockedAppRepository {

    override fun getAllApps(): Flow<List<BlockedAppEntity>> = blockedAppDao.getAll()

    override fun getActiveBlockedApps(): Flow<List<BlockedAppEntity>> = blockedAppDao.getActiveBlockedApps()

    override suspend fun getActiveBlockedAppsSync(): List<BlockedAppEntity> = blockedAppDao.getActiveBlockedAppsSync()

    override fun getBlockedPackageNames(): Flow<List<String>> = blockedAppDao.getBlockedPackageNames()

    override suspend fun setAppBlocked(packageName: String, appName: String, isBlocked: Boolean) {
        blockedAppDao.insertOrUpdate(
            BlockedAppEntity(
                packageName = packageName,
                appName = appName,
                isBlocked = isBlocked
            )
        )
    }

    override suspend fun removeApp(packageName: String) {
        blockedAppDao.deleteByPackageName(packageName)
    }

    override suspend fun logBypass(durationSeconds: Int, reason: String): Long {
        return emergencyBypassLogDao.insert(
            EmergencyBypassLogEntity(
                durationSeconds = durationSeconds,
                reason = reason
            )
        )
    }

    override fun getBypassLogs(): Flow<List<EmergencyBypassLogEntity>> = emergencyBypassLogDao.getAll()
}
