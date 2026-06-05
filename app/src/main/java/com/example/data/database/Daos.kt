package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE contact = :contact LIMIT 1")
    suspend fun getProfile(contact: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Query("UPDATE user_profiles SET isPro = :isPro, proExpiryTimestamp = :expiry WHERE contact = :contact")
    suspend fun updateSubscription(contact: String, isPro: Boolean, expiry: Long)

    @Query("DELETE FROM user_profiles WHERE contact = :contact")
    suspend fun deleteProfile(contact: String)
}

@Dao
interface JobPostDao {
    @Query("SELECT * FROM job_posts ORDER BY lastDate DESC")
    fun getAllJobs(): Flow<List<JobPost>>

    @Query("SELECT * FROM job_posts WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: String): JobPost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<JobPost>)
}

@Dao
interface SavedJobDao {
    @Query("SELECT * FROM saved_jobs")
    fun getSavedJobIds(): Flow<List<SavedJob>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveJob(savedJob: SavedJob)

    @Query("DELETE FROM saved_jobs WHERE id = :jobId")
    suspend fun unsaveJob(jobId: String)

    @Query("SELECT COUNT(*) FROM saved_jobs WHERE id = :jobId")
    suspend fun isJobSaved(jobId: String): Int
}

@Dao
interface AdmitCardDao {
    @Query("SELECT * FROM admit_cards ORDER BY releaseDate DESC")
    fun getAllAdmitCards(): Flow<List<AdmitCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmitCards(cards: List<AdmitCard>)
}

@Dao
interface ResultPostDao {
    @Query("SELECT * FROM results_posts ORDER BY resultDate DESC")
    fun getAllResults(): Flow<List<ResultPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ResultPost>)
}

@Dao
interface AppNotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification)

    @Query("DELETE FROM app_notifications")
    suspend fun clearAll()
}

@Dao
interface ExamCalendarDao {
    @Query("SELECT * FROM exam_calendars ORDER BY examScheduledDate ASC")
    fun getAllCalendars(): Flow<List<ExamCalendarEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvents(events: List<ExamCalendarEvent>)

    @Query("DELETE FROM exam_calendars")
    suspend fun clearAll()
}
