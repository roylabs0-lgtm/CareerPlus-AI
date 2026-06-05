package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "user_profiles")
@JsonClass(generateAdapter = true)
data class UserProfile(
    @PrimaryKey val contact: String, // email or phone
    val fullName: String,
    val mobileNo: String,
    val state: String,
    val qualification: String,
    val age: String,
    val category: String,
    val preferredLanguage: String,
    val isPro: Boolean = false,
    val proExpiryTimestamp: Long = 0L
)

@Entity(tableName = "job_posts")
@JsonClass(generateAdapter = true)
data class JobPost(
    @PrimaryKey val id: String,
    val title: String,
    val department: String,
    val organization: String,
    val vacancyCount: String,
    val qualification: String,
    val ageLimit: String,
    val salary: String,
    val applicationFees: String,
    val startDate: String,
    val lastDate: String,
    val feePaymentDate: String,
    val correctionDate: String,
    val examDate: String,
    val admitCardDate: String,
    val resultDate: String,
    val applyLink: String,
    val officialWebsite: String,
    val officialNotificationPdf: String,
    val categoryGroup: String, // Central, Rajasthan, Uttar Pradesh etc.
    val organizationAbbr: String // SSC, UPSC, Railway, IBPS, RPSC, etc.
)

@Entity(tableName = "saved_jobs")
data class SavedJob(
    @PrimaryKey val id: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "admit_cards")
@JsonClass(generateAdapter = true)
data class AdmitCard(
    @PrimaryKey val id: String,
    val examName: String,
    val releaseDate: String,
    val examDate: String,
    val downloadLink: String
)

@Entity(tableName = "results_posts")
@JsonClass(generateAdapter = true)
data class ResultPost(
    @PrimaryKey val id: String,
    val examName: String,
    val resultDate: String,
    val resultLink: String
)

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val type: String, // info, deadline, update
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "exam_calendars")
@JsonClass(generateAdapter = true)
data class ExamCalendarEvent(
    @PrimaryKey val id: String,
    val organization: String,         // e.g. "SSC", "UPSC", "Railway (RRB)", "IBPS Bank", "RPSC"
    val examName: String,             // e.g. "Civil Services Exam 2026", "SSC CGL Exam 2026"
    val officialNotificationDate: String,
    val applyStartDate: String,
    val applyLastDate: String,
    val examScheduledDate: String,
    val status: String,               // e.g. "Confirmed Calendar Out", "Registration Opened", "Exam Completed"
    val officialCalendarPdfLink: String,
    val sourceWebsite: String         // e.g. "https://ssc.gov.in"
)
