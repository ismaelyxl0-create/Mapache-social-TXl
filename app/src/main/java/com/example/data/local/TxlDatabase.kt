package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

@Database(
    entities = [
        UserEntity::class,
        CommunityEntity::class,
        MembershipEntity::class,
        VoiceParticipantEntity::class,
        RoomEventEntity::class,
        ChatMessageEntity::class,
        DriftingBottleEntity::class,
        NotificationEntity::class,
        CustomFrameEntity::class,
        PostEntity::class,
        PostCommentEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class TxlDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun communityDao(): CommunityDao
    abstract fun membershipDao(): MembershipDao
    abstract fun voiceRoomDao(): VoiceRoomDao
    abstract fun chatDao(): ChatDao
    abstract fun driftingBottleDao(): DriftingBottleDao
    abstract fun notificationDao(): NotificationDao
    abstract fun customFrameDao(): CustomFrameDao
    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var INSTANCE: TxlDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN isDeveloper INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE users SET isDeveloper = 1 WHERE LOWER(email) = 'abukeje26@gmail.com'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE users SET isDeveloper = 0, appRankId = 'none' WHERE LOWER(email) != 'abukeje26@gmail.com'")
                db.execSQL("UPDATE users SET isDeveloper = 1 WHERE LOWER(email) = 'abukeje26@gmail.com'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN audioUri TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN audioDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN isBanned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN banReason TEXT")
            }
        }

        fun getInstance(context: Context): TxlDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TxlDatabase::class.java,
                    "txlsocial_db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object SeedData {
    fun hashPassword(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun ensureSeeded(@Suppress("UNUSED_PARAMETER") db: TxlDatabase) {
        // Sin bots ni salas falsas: solo datos creados por usuarios reales.
    }
}
