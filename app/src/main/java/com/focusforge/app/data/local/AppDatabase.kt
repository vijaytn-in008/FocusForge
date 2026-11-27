package com.focusforge.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusforge.app.data.local.dao.BlockedAppAttemptDao
import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.local.dao.CustomDomainDao
import com.focusforge.app.data.local.dao.DnsBlockLogDao
import com.focusforge.app.data.local.dao.DomainBlockCategoryDao
import com.focusforge.app.data.local.dao.EmergencyBypassLogDao
import com.focusforge.app.data.local.dao.ProfileDao
import com.focusforge.app.data.local.dao.SessionDao
import com.focusforge.app.data.local.entity.BlockedAppAttemptEntity
import com.focusforge.app.data.local.entity.BlockedAppEntity
import com.focusforge.app.data.local.entity.CustomDomainEntity
import com.focusforge.app.data.local.entity.DnsBlockLogEntity
import com.focusforge.app.data.local.entity.DomainBlockCategoryEntity
import com.focusforge.app.data.local.entity.EmergencyBypassLogEntity
import com.focusforge.app.data.local.entity.ProfileEntity
import com.focusforge.app.data.local.entity.SessionEntity

/**
 * Main Room Database for FocusForge.
 * Strictly local-first.
 */
@Database(
    entities = [
        SessionEntity::class,
        BlockedAppEntity::class,
        EmergencyBypassLogEntity::class,
        BlockedAppAttemptEntity::class,
        ProfileEntity::class,
        DomainBlockCategoryEntity::class,
        CustomDomainEntity::class,
        DnsBlockLogEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun emergencyBypassLogDao(): EmergencyBypassLogDao
    abstract fun blockedAppAttemptDao(): BlockedAppAttemptDao
    abstract fun profileDao(): ProfileDao
    abstract fun domainBlockCategoryDao(): DomainBlockCategoryDao
    abstract fun customDomainDao(): CustomDomainDao
    abstract fun dnsBlockLogDao(): DnsBlockLogDao

    companion object {
        const val DATABASE_NAME = "focusforge_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_apps` (
                        `packageName` TEXT NOT NULL PRIMARY KEY,
                        `appName` TEXT NOT NULL,
                        `isBlocked` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `emergency_bypass_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_app_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `activeProfile` TEXT NOT NULL DEFAULT 'OFF',
                        `updatedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `profiles` (`id`, `activeProfile`, `updatedAt`)
                    VALUES ('default', 'OFF', ${System.currentTimeMillis()})
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `domain_block_categories` (
                        `category` TEXT NOT NULL PRIMARY KEY,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `profileId` TEXT NOT NULL DEFAULT 'default'
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT OR IGNORE INTO `domain_block_categories` (`category`, `isEnabled`, `profileId`) VALUES ('ADULT', 1, 'default')")
                db.execSQL("INSERT OR IGNORE INTO `domain_block_categories` (`category`, `isEnabled`, `profileId`) VALUES ('DOH_ENDPOINTS', 1, 'default')")
                db.execSQL("INSERT OR IGNORE INTO `domain_block_categories` (`category`, `isEnabled`, `profileId`) VALUES ('DISTRACTION', 0, 'default')")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_domains` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `listType` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dns_block_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `wasBlocked` INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
