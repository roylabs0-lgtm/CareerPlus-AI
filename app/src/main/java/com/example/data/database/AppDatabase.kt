package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.*

@Database(
    entities = [
        UserProfile::class,
        JobPost::class,
        SavedJob::class,
        AdmitCard::class,
        ResultPost::class,
        AppNotification::class,
        ExamCalendarEvent::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun jobPostDao(): JobPostDao
    abstract fun savedJobDao(): SavedJobDao
    abstract fun admitCardDao(): AdmitCardDao
    abstract fun resultPostDao(): ResultPostDao
    abstract fun appNotificationDao(): AppNotificationDao
    abstract fun examCalendarDao(): ExamCalendarDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "careerplus_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
