package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

object AppLocalizer {
    private var sharedPrefs: SharedPreferences? = null
    val aiTranslationsCache = mutableMapOf<String, MutableMap<String, String>>()
    private val pendingRequests = Collections.synchronizedSet(mutableSetOf<String>())
    private val localizerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    var onTranslationUpdated: (() -> Unit)? = null

    fun initialize(context: Context) {
        sharedPrefs = context.getSharedPreferences("ai_translations_cache", Context.MODE_PRIVATE)
        val editor = sharedPrefs?.edit()
        var changed = false
        sharedPrefs?.all?.forEach { (key, value) ->
            if (value is String) {
                val lowercaseVal = value.lowercase()
                val isBad = lowercaseVal.contains("unavailable") ||
                            lowercaseVal.contains("temporarily") ||
                            lowercaseVal.contains("error") ||
                            lowercaseVal.contains("configure") ||
                            lowercaseVal.contains("gemini") ||
                            lowercaseVal.contains("coach") ||
                            lowercaseVal.contains("try again") ||
                            lowercaseVal.contains("connection") ||
                            value.length > 90
                
                if (isBad) {
                    editor?.remove(key)
                    changed = true
                } else {
                    val parts = key.split(":::", limit = 2)
                    if (parts.size == 2) {
                        val lang = parts[0]
                        val original = parts[1]
                        aiTranslationsCache.getOrPut(lang) { mutableMapOf() }[original] = value
                    }
                }
            }
        }
        if (changed) {
            editor?.apply()
        }
    }

    private fun enqueueTranslation(text: String, language: String) {
        val key = "$language:::$text"
        if (pendingRequests.contains(key)) return
        pendingRequests.add(key)

        localizerScope.launch(Dispatchers.IO) {
            val prompt = """
                Translate the following UI label or phrase from a government exams preparation and counseling application into highly realistic, professional, clear, natural, and perfectly readable $language.
                Under no circumstances should its translation sound like literal machine translation or Google Translate; it must look completely native, clear, and authentic to native speakers.
                
                Text: "$text"
                
                Provide ONLY the direct translation. Do not include quote marks, formatting, explanations, or introductory text.
            """.trimIndent()

            val systemInstruction = "You are an expert bilingual native translator of applications. Provide only perfect, natural, and clean translations."
            val result = RetrofitClient.getGeminiResponse(prompt, systemInstruction)

            val lowercaseRes = result.lowercase()
            val isErrorResponse = result.contains("Please configure your Gemini") || 
                                  result.contains("Consultation Error") || 
                                  lowercaseRes.contains("temporarily") ||
                                  lowercaseRes.contains("unavailable") ||
                                  lowercaseRes.contains("api key") ||
                                  lowercaseRes.contains("try again") ||
                                  lowercaseRes.contains("connection") ||
                                  result.contains("No suggestions found") ||
                                  result.contains("AI Coach") ||
                                  result.isBlank() ||
                                  result.length > 95

            if (!isErrorResponse) {
                val cleanedResult = result.removeSurrounding("\"").trim()
                if (cleanedResult.isNotBlank() && cleanedResult != text) {
                    withContext(Dispatchers.Main) {
                        aiTranslationsCache.getOrPut(language) { mutableMapOf() }[text] = cleanedResult
                        sharedPrefs?.edit()?.putString(key, cleanedResult)?.apply()
                        pendingRequests.remove(key)
                        onTranslationUpdated?.invoke()
                    }
                    return@launch
                }
            }
            pendingRequests.remove(key)
        }
    }

    private val translations = mapOf(
        "Hindi" to mapOf(
            "Home" to "होम",
            "Jobs" to "नौकरियां",
            "Calendar" to "कैलेंडर",
            "Admit Cards" to "प्रवेश पत्र",
            "Results" to "परिणाम",
            "Coach/User" to "एआई कोच",
            "CareerPlus AI" to "करियरप्लस एआई",
            "Never Miss an Opportunity" to "अवसर कभी न गंवाएं",
            "Government Exam Calendars" to "सरकारी परीक्षा कैलेंडर",
            "Admit Cards & Hall Tickets" to "प्रवेश पत्र और परीक्षा विवरण",
            "Results Declared" to "घोषित परिणाम",
            "Apply Last Date" to "आवेदन की अंतिम तिथि",
            "Salary" to "वेतन",
            "Age Limit" to "आयु सीमा",
            "Qualification" to "योग्यता",
            "State" to "राज्य",
            "Official Website" to "आधिकारिक वेबसाइट",
            "Download Calendar" to "कैलेंडर डाउनलोड",
            "Download Admission Certificate (PDF)" to "प्रवेश पत्र डाउनलोड (PDF)",
            "Download Hall Ticket (PDF)" to "प्रवेश पत्र डाउनलोड (PDF)",
            "AI Coach" to "एआई कोच",
            "Search Government Jobs..." to "सरकारी नौकरियां खोजें...",
            "Ask AI Coach..." to "एआई कोच से पूछें...",
            "Active Jobs" to "सक्रिय नौकरियां",
            "Latest Announced Results" to "नवीनतम घोषित परिणाम",
            "Check Merit List & Result PDF" to "मेरिट सूची देखें (PDF)",
            "Search Results..." to "परिणाम खोजें...",
            "Notifications" to "सूचनाएं",
            "Profile Information" to "प्रोफ़ाइल जानकारी",
            "Full Name" to "पूरा नाम",
            "Select State" to "राज्य चुनें",
            "Select Language" to "भाषा चुनें",
            "Your Mobile Number (Verification & Updates)" to "आपका मोबाइल नंबर (सत्यापन और अपडेट)",
            "Your Age in Years" to "आपकी आयु वर्षों में",
            "Selected Category" to "चयनित श्रेणी",
            "Create Profile" to "प्रोफ़ाइल बनाएं",
            "Update Profile" to "प्रोफ़ाइल अपडेट करें",
            "Access My Career Hub" to "कैरियर हब में प्रवेश करें",
            "Enter your 10-digit mobile number or Email to proceed" to "आगे बढ़ने के लिए अपना 10-अंकीय मोबाइल नंबर या ईमेल दर्ज करें",
            "Official Calendars" to "आधिकारिक कैलेंडर",
            "All Notifications" to "सभी सूचनाएं",
            "Clear All" to "सभी साफ़ करें",
            "Automatic Scanner Engine Active" to "स्वचालित स्कैनर सक्रिय",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "आधिकारिक परीक्षा पोर्टलों ssc.gov.in, upsc.gov.in और RRB से प्रति घंटे प्रमाणित सामग्री। पूरी तरह से वास्तविक सरकारी अपडेट।",
            "Released:" to "जारी हुआ:",
            "View Calendars" to "कैलेंडर देखें",
            "Latest Jobs Tracker" to "नवीनतम नौकरियां ट्रैकर",
            "Profile & AI Coach" to "प्रोफ़ाइल और एआई कोच",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "आयोग से विवरण सत्यापित करें। हॉल टिकट की प्रति डाउनलोड करें और अपने पास रखें।",
            "Verified Exam Hall Ticket" to "सत्यापित परीक्षा हॉल टिकट",
            "Paper Scheduled Date:" to "परीक्षा निर्धारित तिथि:",
            "View Admit Cards" to "प्रवेश पत्र देखें",
            "No announcements yet." to "कोई घोषणा नहीं।",
            "Active Jobs List" to "सक्रिय नौकरियों की सूची",
            "Quick Alerts" to "त्वरित अलर्ट",
            "Government Jobs" to "सरकारी नौकरियां",
            "Enter Full Name" to "पूरा नाम दर्ज करें",
            "Age (Yrs)" to "आयु (वर्ष)",
            "Preferred Reading Language" to "पठन भाषा",
            "Verify & Access Hub" to "सत्यापित करें और प्रवेश करें",
            "Select Category Group" to "श्रेणी समूह चुनें"
        ),
        "Marathi" to mapOf(
            "Home" to "होम",
            "Jobs" to "नोकऱ्या",
            "Calendar" to "कॅलेंडर",
            "Admit Cards" to "प्रवेशपत्र",
            "Results" to "निकाल",
            "Coach/User" to "एआय कोच",
            "CareerPlus AI" to "करिअरप्लस एआय",
            "Never Miss an Opportunity" to "संधी कधीही सोडू नका",
            "Government Exam Calendars" to "सरकारी परीक्षांचे वेळापत्रक",
            "Admit Cards & Hall Tickets" to "प्रवेशपत्र आणि परीक्षा तपशील",
            "Results Declared" to "निकाल घोषित",
            "Apply Last Date" to "अर्ज करण्याची शेवटची तारीख",
            "Salary" to "वेतन",
            "Age Limit" to "वयोमर्यादा",
            "Qualification" to "पात्रता",
            "State" to "राज्य",
            "Official Website" to "अधिकृत संकेतस्थळ",
            "Download Calendar" to "कॅलेंडर डाउनलोड",
            "Download Admission Certificate (PDF)" to "प्रवेशपत्र डाउनलोड करा (PDF)",
            "Download Hall Ticket (PDF)" to "प्रवेशपत्र डाउनलोड करा (PDF)",
            "AI Coach" to "एआय कोच",
            "Search Government Jobs..." to "सरकारी नोकऱ्या शोधा...",
            "Ask AI Coach..." to "एआय कोचला विचारा...",
            "Active Jobs" to "सक्रिय नोकऱ्या",
            "Latest Announced Results" to "नवीनतम घोषित निकाल",
            "Check Merit List & Result PDF" to "गुणवत्ता यादी आणि निकाल (PDF)",
            "Search Results..." to "निकाल शोधा...",
            "Notifications" to "सूचना",
            "Profile Information" to "प्रोफाइल माहिती",
            "Full Name" to "पूर्ण नाव",
            "Select State" to "राज्य निवडा",
            "Select Language" to "भाषा निवडा",
            "Your Mobile Number (Verification & Updates)" to "तुमचा मोबाईल नंबर (पडताळणी आणि अपडेट्स)",
            "Your Age in Years" to "तुमचे वय वर्ष",
            "Selected Category" to "निवडलेली श्रेणी",
            "Create Profile" to "प्रोफाइल तयार करा",
            "Update Profile" to "प्रोफाइल अपडेट करा",
            "Access My Career Hub" to "कॅरियर हबमध्ये प्रवेश करा",
            "Enter your 10-digit mobile number or Email to proceed" to "प्रक्रिया सुरू करण्यासाठी १०-अंकी मोबाईल नंबर किंवा ईमेल प्रविष्ट करा",
            "Official Calendars" to "अधिकृत कॅलेंडर",
            "All Notifications" to "सर्व सूचना",
            "Clear All" to "सर्व साफ करा",
            "Automatic Scanner Engine Active" to "स्वयंचलित स्कॅनर सक्रिय",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "एसएससी, यूपीएससी आणि रेल्वे अधिकृत संकेतस्थळांवरून प्रति तास पडताळलेला मजकूर. पूर्णपणे अधिकृत सरकारी अपडेट्स.",
            "Released:" to "प्रसिद्ध झाले:",
            "View Calendars" to "कॅलेंडर पहा",
            "Latest Jobs Tracker" to "नोकरी ट्रॅकर",
            "Profile & AI Coach" to "प्रोफाइल आणि एआय कोच",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "आयोगाकडून तपशील तपासा. हॉल तिकीट डाउनलोड करा.",
            "Verified Exam Hall Ticket" to "सत्यापित परीक्षा हॉल तिकीट",
            "Paper Scheduled Date:" to "परीक्षा नियोजित तारीख:",
            "View Admit Cards" to "प्रवेशपत्र पहा",
            "No announcements yet." to "सध्या कोणतीही घोषणा नाही.",
            "Active Jobs List" to "सक्रिय नोकऱ्यांची यादी",
            "Quick Alerts" to "त्वरित अलर्ट",
            "Government Jobs" to "सरकारी नोकऱ्या",
            "Enter Full Name" to "पूर्ण नाव प्रविष्ट करा",
            "Age (Yrs)" to "वय (वर्षे)",
            "Preferred Reading Language" to "पसंतीची भाषा",
            "Verify & Access Hub" to "सत्यापित करा आणि प्रवेश करा",
            "Select Category Group" to "श्रेणी समूह निवडा"
        ),
        "Gujarati" to mapOf(
            "Home" to "હોમ",
            "Jobs" to "નોકરીઓ",
            "Calendar" to "કેલેન્ડર",
            "Admit Cards" to "પ્રવેશપત્ર",
            "Results" to "પરિણામો",
            "Coach/User" to "AI કોચ",
            "CareerPlus AI" to "કરિયરપ્લસ AI",
            "Never Miss an Opportunity" to "તક ક્યારેય ગુમાવશો નહીં",
            "Government Exam Calendars" to "સરકારી પરીક્ષા કેલેન્ડर",
            "Admit Cards & Hall Tickets" to "પ્રવેશપત્રો અને પરીક્ષા વિગત",
            "Results Declared" to "જાહેર થયેલ પરિણામો",
            "Apply Last Date" to "અરજી કરવાની છેલ્લી તારીખ",
            "Salary" to "પગાર",
            "Age Limit" to "વય મર્યાદા",
            "Qualification" to "લાયકાત",
            "State" to "રાજ્ય",
            "Official Website" to "સત્તાવાર વેબસાઇટ",
            "Download Calendar" to "કેલેન્ડર ડાઉનલોડ",
            "Download Admission Certificate (PDF)" to "પ્રવેશપત્ર ડાઉનલોડ કરો (PDF)",
            "Download Hall Ticket (PDF)" to "પ્રવેશપત્ર ડાઉનલોડ કરો (PDF)",
            "AI Coach" to "AI કોચ",
            "Search Government Jobs..." to "સરકારી નોકરીઓ શોધો...",
            "Ask AI Coach..." to "AI કોચને પૂછો...",
            "Active Jobs" to "સક્રિય નોકરીઓ",
            "Latest Announced Results" to "નવીનતમ જાહેર થયેલ પરિણામો",
            "Check Merit List & Result PDF" to "મેરિટ લિસ્ટ અને પરિણામ (PDF)",
            "Search Results..." to "પરિણામો શોધો...",
            "Notifications" to "સૂચનાઓ",
            "Profile Information" to "પ્રોફાઇલ માહિતી",
            "Full Name" to "પૂરું નામ",
            "Select State" to "રાજ્ય પસંદ કરો",
            "Select Language" to "ભાષા પસંદ કરો",
            "Your Mobile Number (Verification & Updates)" to "તમારો મોબાઈલ નંબર (ચકાસણી અને અપડેટ્સ)",
            "Your Age in Years" to "તમારી ઉંમર વર્ષોમાં",
            "Selected Category" to "પસંદ કરેલ કેટેગરી",
            "Create Profile" to "પ્રોફાઇલ બનાવો",
            "Update Profile" to "પ્રોફાઇલ અપડેટ કરો",
            "Access My Career Hub" to "કેરિયર હબમાં પ્રવેશ મેળવો",
            "Enter your 10-digit mobile number or Email to proceed" to "આગળ વધવા માટે તમારો ૧૦-અંકનો મોબાઈલ નંબર અથવા ઈમેલ દાખલ કરો",
            "Official Calendars" to "સત્તાવાર કેલેન્ડર",
            "All Notifications" to "બધી સૂચનાઓ",
            "Clear All" to "બધું સાફ કરો",
            "Automatic Scanner Engine Active" to "સ્વચાલિત સ્કેનર સક્રિય",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "સત્તાવાર પરીક્ષા પોર્ટલ પરથી દર કલાકે પ્રમાણિત સામગ્રી.",
            "Released:" to "જાહેર થયું:",
            "View Calendars" to "કેલેન્ડર જુઓ",
            "Latest Jobs Tracker" to "નોકરીઓ ટ્રેકર",
            "Profile & AI Coach" to "પ્રોફાઇલ અને AI કોચ",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "સત્તાવાર કમિશનથી માહિતી ચકાસો. હોલ ટિકિટ ડાઉનલોડ કરો.",
            "Verified Exam Hall Ticket" to "ચકાસાયેલ પરીક્ષા હોલ ટિકિટ",
            "Paper Scheduled Date:" to "પરીક્ષાની તારીખ:",
            "View Admit Cards" to "પ્રવેશપત્ર જુઓ",
            "No announcements yet." to "હજી કોઈ જાહેરાત નથી.",
            "Active Jobs List" to "સક્રિય નોકરીઓની સૂચિ",
            "Quick Alerts" to "ઝડપી એલર્ટ",
            "Government Jobs" to "સરકારી નોકરીઓ",
            "Enter Full Name" to "પૂરું નામ દાખલ કરો",
            "Age (Yrs)" to "ઉંમર (વર્ષ)",
            "Preferred Reading Language" to "વાંચવાની ભાષા",
            "Verify & Access Hub" to "ચકાસો અને પ્રવેશો",
            "Select Category Group" to "કેટેગરી જૂથ પસંદ કરો"
        ),
        "Bengali" to mapOf(
            "Home" to "হোম",
            "Jobs" to "চাকরি",
            "Calendar" to "ক্যালেন্ডার",
            "Admit Cards" to "অ্যাডমিট কার্ড",
            "Results" to "ফলাফল",
            "Coach/User" to "এআই কোচ",
            "CareerPlus AI" to "ক্যারিয়ারপ্লাস এআই",
            "Never Miss an Opportunity" to "কখনও সুযোগ হারাবেন না",
            "Government Exam Calendars" to "সরকারি পরীক্ষার ক্যালেন্ডার",
            "Admit Cards & Hall Tickets" to "অ্যাডমিট কার্ড এবং পরীক্ষার বিবরণ",
            "Results Declared" to "ঘোষিত ফলাফল",
            "Apply Last Date" to "আবেদনের শেষ তারিখ",
            "Salary" to "বেতন",
            "Age Limit" to "বয়সসীমা",
            "Qualification" to "যোগ্যতা",
            "State" to "রাজ্য",
            "Official Website" to "অফিসিয়াল ওয়েবসাইট",
            "Download Calendar" to "ক্যালেন্ডার ডাউনলোড",
            "Download Admission Certificate (PDF)" to "অ্যাডমিট কার্ড ডাউনলোড (PDF)",
            "Download Hall Ticket (PDF)" to "অ্যাডমিট কার্ড ডাউনলোড (PDF)",
            "AI Coach" to "এআই কোচ",
            "Search Government Jobs..." to "সরকারি চাকরি খুঁজুন...",
            "Ask AI Coach..." to "এআই কোচকে জিজ্ঞাসা করুন...",
            "Active Jobs" to "সক্রিয় চাকরি",
            "Latest Announced Results" to "সর্বশেষ ঘোষিত ফলাফল",
            "Check Merit List & Result PDF" to "মেরিট লিস্ট এবং ফলাফল (PDF)",
            "Search Results..." to "ফলাফল খুঁজুন...",
            "Notifications" to "বিজ্ঞপ্তি",
            "Profile Information" to "প্রোফাইল তথ্য",
            "Full Name" to "পুরো নাম",
            "Select State" to "রাজ্য নির্বাচন করুন",
            "Select Language" to "ভাষা নির্বাচন করুন",
            "Your Mobile Number (Verification & Updates)" to "আপনার মোবাইল নম্বর (ভেরিফিকেশন ও আপডেট)",
            "Your Age in Years" to "আপনার বয়স বছর",
            "Selected Category" to "নির্বাচিত বিভাগ",
            "Create Profile" to "প্রোফাইল তৈরি করুন",
            "Update Profile" to "প্রোফাইল আপডেট করুন",
            "Access My Career Hub" to "ক্যারিয়ার হাবে প্রবেশ করুন",
            "Enter your 10-digit mobile number or Email to proceed" to "এগিয়ে যেতে আপনার ১০-ডিজিটের মোবাইল নম্বর বা ইমেল লিখুন",
            "Official Calendars" to "অফিসিয়াল ক্যালেন্ডার",
            "All Notifications" to "সমস্ত বিজ্ঞপ্তি",
            "Clear All" to "সব পরিষ্কার করুন",
            "Automatic Scanner Engine Active" to "স্বয়ংক্রিয় স্ক্যানার সক্রিয়",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "অফিসিয়াল পোর্টাল থেকে প্রতি ঘন্টায় যাচাইকৃত আপডেট।",
            "Released:" to "প্রকাশিত:",
            "View Calendars" to "ক্যালেন্ডার দেখুন",
            "Latest Jobs Tracker" to "চাকরি ট্র্যাকার",
            "Profile & AI Coach" to "প্রোফাইল ও এআই কোচ",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "কমিশন থেকে বিস্তারিত যাচাই করুন। হল টিকিট ডাউনলোড করুন।",
            "Verified Exam Hall Ticket" to "যাচাইকৃত পরীক্ষার হল টিকিট",
            "Paper Scheduled Date:" to "পরীক্ষার নির্ধারিত তারিখ:",
            "View Admit Cards" to "অ্যাডমিট কার্ড দেখুন",
            "No announcements yet." to "এখনো কোনো ঘোষণা নেই।",
            "Active Jobs List" to "সক্রিয় চাকরির তালিকা",
            "Quick Alerts" to "দ্রুত সতর্কতা",
            "Government Jobs" to "সরকারি চাকরি",
            "Enter Full Name" to "সম্পূর্ণ নাম লিখুন",
            "Age (Yrs)" to "বয়স (বছর)",
            "Preferred Reading Language" to "পছন্দের ভাষা",
            "Verify & Access Hub" to "যাচাই এবং প্রবেশ",
            "Select Category Group" to "শ্রেণী গ্রুপ নির্বাচন করুন"
        ),
        "Tamil" to mapOf(
            "Home" to "முகப்பு",
            "Jobs" to "பணிகள்",
            "Calendar" to "நாட்காட்டி",
            "Admit Cards" to "அனுமதி சீட்டு",
            "Results" to "முடிவுகள்",
            "Coach/User" to "ஏஐ பயிற்சியாளர்",
            "CareerPlus AI" to "கரியர்பிளஸ் ஏஐ",
            "Never Miss an Opportunity" to "வாய்ப்பை ஒருபோதும் தவறவிடாதீர்கள்",
            "Government Exam Calendars" to "அரசு தேர்வு நாட்காட்டி",
            "Admit Cards & Hall Tickets" to "நுழைவுச்சீட்டுகள் மற்றும் தேர்வு விவரங்கள்",
            "Results Declared" to "வெளியான முடிவுகள்",
            "Apply Last Date" to "விண்ணப்பிக்க கடைசி தேதி",
            "Salary" to "சம்பளம்",
            "Age Limit" to "வயது வரம்பு",
            "Qualification" to "தகுதி",
            "State" to "மாநிலம்",
            "Official Website" to "அதிகாரப்பூர்வ இணையதளம்",
            "Download Calendar" to "நாட்காட்டி பதிவிறக்கு",
            "Download Admission Certificate (PDF)" to "நுழைவுச்சீட்டு பதிவிறக்கு (PDF)",
            "Download Hall Ticket (PDF)" to "நுழைவுச்சீட்டு பதிவிறக்கு (PDF)",
            "AI Coach" to "ஏஐ பயிற்சியாளர்",
            "Search Government Jobs..." to "அரசு பணிகளைத் தேடுக...",
            "Ask AI Coach..." to "ஏஐ பயிற்சியாளரிடம் கேட்க...",
            "Active Jobs" to "செயலில் உள்ள பணிகள்",
            "Latest Announced Results" to "சமீபத்திய தேர்வு முடிவுகள்",
            "Check Merit List & Result PDF" to "தகுதிப் பட்டியல் முடிவுகள் (PDF)",
            "Search Results..." to "முடிவுகளைத் தேடுக...",
            "Notifications" to "அறிவிப்புகள்",
            "Profile Information" to "சுயவிவர தகவல்",
            "Full Name" to "முழு பெயர்",
            "Select State" to "மாநிலத்தை தேர்வு செய்க",
            "Select Language" to "மொழியை தேர்வு செய்க",
            "Your Mobile Number (Verification & Updates)" to "தொலைபேசி எண் (பதிவு மற்றும் புதுப்பிப்பு)",
            "Your Age in Years" to "வயது (ஆண்டுகளில்)",
            "Selected Category" to "தேர்ந்தெடுக்கப்பட்ட பிரிவு",
            "Create Profile" to "விவரத்தை உருவாக்கு",
            "Update Profile" to "விவரங்களை புதுப்பி",
            "Access My Career Hub" to "எனது தொழில் மையத்தை அணுகுக",
            "Enter your 10-digit mobile number or Email to proceed" to "தொடர உங்கள் 10 இலக்க மொபைல் எண் அல்லது மின்னஞ்சலை உள்ளிடவும்",
            "Official Calendars" to "அதிகாரப்பூர்வ காலண்டர்",
            "All Notifications" to "அனைத்து அறிவிப்புகள்",
            "Clear All" to "அனைத்தையும் அழி",
            "Automatic Scanner Engine Active" to "தானியங்கி ஸ்கேனர் செயல்படுகிறது",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "அரசு இணையதளங்கள் ssc.gov.in, upsc.gov.in, RRB ஆகியவற்றிலிருந்து நேரடியாக சரிபார்க்கப்பட்டது.",
            "Released:" to "வெளியிடப்பட்டது:",
            "View Calendars" to "நாட்காட்டிகளை காண்க",
            "Latest Jobs Tracker" to "வேலை கண்காணிப்பு",
            "Profile & AI Coach" to "சுயவிவரம் & ஏஐ பயிற்சியாளர்",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "ஆணையத்திலிருந்து விவரங்களை சரிபார்க்கவும். நுழைவு சீட்டை பதிவிறக்கவும்.",
            "Verified Exam Hall Ticket" to "சரிபார்க்கப்பட்ட தேர்வு நுழைவு சீட்டு",
            "Paper Scheduled Date:" to "தேர்வுக்கான தேதி:",
            "View Admit Cards" to "நுழைவு சீට්ටுகளை காண்க",
            "No announcements yet." to "தற்போது அறிவிப்புகள் ஏதுமில்லை.",
            "Active Jobs List" to "செயலில் உள்ள வேலைகள்",
            "Quick Alerts" to "விரைவு எச்சரிக்கைகள்",
            "Government Jobs" to "அரசு வேலைகள்",
            "Enter Full Name" to "முழு பெயரை உள்ளிடுக",
            "Age (Yrs)" to "வயது (ஆண்டுகள்)",
            "Preferred Reading Language" to "விருப்பமான மொழி",
            "Verify & Access Hub" to "சரிபார்த்து பயன்படுத்தவும்",
            "Select Category Group" to "பிரிவு குழுவை தேர்க"
        ),
        "Telugu" to mapOf(
            "Home" to "హోమ్",
            "Jobs" to "ఉద్యోగాలు",
            "Calendar" to "క్యాలెండర్",
            "Admit Cards" to "అడ్మిట్ కార్డ్స్",
            "Results" to "ఫలితాలు",
            "Coach/User" to "AI కోచ్",
            "CareerPlus AI" to "కెరీర్‌ప్లస్ AI",
            "Never Miss an Opportunity" to "అవకాశాన్ని ఎప్పటికీ కోల్పోకండి",
            "Government Exam Calendars" to "ప్రభుత్వ పరీక్షల క్యాలెండర్",
            "Admit Cards & Hall Tickets" to "అడ్మిట్ కార్డులు & పరీక్ష వివరాలు",
            "Results Declared" to "ప్రకటించిన ఫలితాలు",
            "Apply Last Date" to "దరఖాస్తుకు చివరి తేదీ",
            "Salary" to "జీతం",
            "Age Limit" to "వయోపరిమితి",
            "Qualification" to "అర్హత",
            "State" to "రాష్ట్రం",
            "Official Website" to "అధికారిక వెబ్‌సైట్",
            "Download Calendar" to "క్యాలెండర్ డౌన్‌లోడ్",
            "Download Admission Certificate (PDF)" to "అడ్మిట్ కార్డ్ డౌన్‌లోడ్ (PDF)",
            "Download Hall Ticket (PDF)" to "అడ్మిట్ కార్డ్ డౌన్‌లోడ్ (PDF)",
            "AI Coach" to "AI కోచ్",
            "Search Government Jobs..." to "ప్రభుత్వ ఉద్యోగాల కోసం వెతకండి...",
            "Ask AI Coach..." to "AI కోచ్‌ని అడగండి...",
            "Active Jobs" to "యాక్టివ్ ఉద్యోగాలు",
            "Latest Announced Results" to "తాజా ఫలితాలు",
            "Check Merit List & Result PDF" to "మెరిట్ లిస్ట్ & ఫలితం (PDF)",
            "Search Results..." to "ఫలితాలను వెతకండి...",
            "Notifications" to "నోటిఫिकేషన్లు",
            "Profile Information" to "ప్రొఫైల్ సమాచారం",
            "Full Name" to "పూర్తి పేరు",
            "Select State" to "రాష్ట్రాన్ని ఎంచుకోండి",
            "Select Language" to "భాషను ఎంచుకోండి",
            "Your Mobile Number (Verification & Updates)" to "మీ మొబైల్ నంబర్ (ధృవీకరణ & అప్‌డేట్స్)",
            "Your Age in Years" to "మీ వయస్సు సంవత్సరాలలో",
            "Selected Category" to "ఎంచుకున్న వర్గం",
            "Create Profile" to "ప్రొఫైల్ సృష్టించండి",
            "Update Profile" to "ప్రొఫైల్ అప్‌డేట్ చేయండి",
            "Access My Career Hub" to "కెరీర్ హబ్‌కి వెళ్ళండి",
            "Enter your 10-digit mobile number or Email to proceed" to "కొనసాగడానికి మీ 10 అంకెల మొబైల్ నంబర్ లేదా ఈమెయిల్ నమోదు చేయండి",
            "Official Calendars" to "అధికారిక క్యాలెండర్లు",
            "All Notifications" to "అన్ని నోటిఫికేషన్లు",
            "Clear All" to "అన్నీ క్లియర్ చేయండి",
            "Automatic Scanner Engine Active" to "ఆటోమేటిక్ స్కానర్ ఆక్టివ్‌గా ఉంది",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "అధికారిక పోర్టల్స్ ssc.gov.in, upsc.gov.in మరియు RRB నుండి ధృవీకరించిన సమాచారం.",
            "Released:" to "విడుదలైంది:",
            "View Calendars" to "క్యాలెండర్లు చూడండి",
            "Latest Jobs Tracker" to "ఉద్యోగాల ట్రాకర్",
            "Profile & AI Coach" to "ప్రొఫైల్ & AI కోచ్",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "కమిషన్ నుండి వివరాలను సరిచూసుకోండి. హాల్ టికెట్ డౌన్‌లోడ్ చేసుకోండి.",
            "Verified Exam Hall Ticket" to "ధృవీకరించబడిన పరీక్ష హాల్ టికెట్",
            "Paper Scheduled Date:" to "పరీక్షScheduled తేదీ:",
            "View Admit Cards" to "అడ్మిట్ కార్డులు చూడండి",
            "No announcements yet." to "ఇంకా ఎలాంటి ప్రకటనలు లేవు.",
            "Active Jobs List" to "యాక్టివ్ ఉద్యోగాల జాబితా",
            "Quick Alerts" to "క్విక్ అలర్ట్స్‌",
            "Government Jobs" to "ప్రభుత్వ ఉద్యోగాలు",
            "Enter Full Name" to "పూర్తి పేరు నమోదు చేయండి",
            "Age (Yrs)" to "వయస్సు (సంవత్సరాలు)",
            "Preferred Reading Language" to "పఠన భాష",
            "Verify & Access Hub" to "ధృవీకరించి ప్రవేశించండి",
            "Select Category Group" to "వర్గం సమూహాన్ని ఎంచుకోండి"
        ),
        "Kannada" to mapOf(
            "Home" to "ಮುಖಪುಟ",
            "Jobs" to "ಉದ್ಯೋಗಗಳು",
            "Calendar" to "ಕ್ಯಾಲೆಂಡರ್",
            "Admit Cards" to "ಪ್ರವೇಶ ಪತ್ರಗಳು",
            "Results" to "ಫಲಿತಾಂಶಗಳು",
            "Coach/User" to "AI ಕೋಚ್",
            "CareerPlus AI" to "ಕೆರಿಯರ್‌ಪ್ಲಸ್ AI",
            "Never Miss an Opportunity" to "ಯಾವತ್ತೂ ಅವಕಾಶ ತಪ್ಪಿಸಬೇಡಿ",
            "Government Exam Calendars" to "ಸರ್ಕಾರಿ ಪರೀಕ್ಷಾ ಕ್ಯಾಲೆಂಡರ್",
            "Admit Cards & Hall Tickets" to "ಪ್ರವೇಶ ಪತ್ರ ಮತ್ತು ಪರೀಕ್ಷಾ ವಿವರ",
            "Results Declared" to "ಫಲಿತಾಂಶ ಪ್ರಕಟಣೆ",
            "Apply Last Date" to "ಅರ್ಜಿ ಸಲ್ಲಿಸಲು ಕೊನೆಯ ದಿನಾಂಕ",
            "Salary" to "ವೇತನ",
            "Age Limit" to "ವಯೋಮಿತಿ",
            "Qualification" to "ಅರ್ಹತೆ",
            "State" to "ರಾಜ್ಯ",
            "Official Website" to "ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್",
            "Download Calendar" to "ಕ್ಯಾಲೆಂಡರ್ ಡೌನ್‌ಲೋಡ್",
            "Download Admission Certificate (PDF)" to "ಪ್ರವೇಶ ಪತ್ರ ಡೌನ್‌ಲೋಡ್ (PDF)",
            "Download Hall Ticket (PDF)" to "ಪ್ರವೇಶ ಪತ್ರ ಡೌನ್‌ಲೋಡ್ (PDF)",
            "AI Coach" to "AI ಕೋಚ್",
            "Search Government Jobs..." to "ಸರ್ಕಾರಿ ಉದ್ಯೋಗಗಳಿಗಾಗಿ ಹುಡುಕಿ...",
            "Ask AI Coach..." to "AI ಕೋಚ್ ಜೊತೆ ಚರ್ಚಿಸಿ...",
            "Active Jobs" to "ಸಕ್ರಿಯ ಉದ್ಯೋಗಗಳು",
            "Latest Announced Results" to "ಇತ್ತೀಚಿನ ಫಲಿತಾಂಶಗಳು",
            "Check Merit List & Result PDF" to "ಮೆರಿಟ್ ಪಟ್ಟಿ ಮತ್ತು ಫಲಿತಾಂಶ (PDF)",
            "Search Results..." to "ಫಲಿತಾಂಶಗಳನ್ನು ಹುಡುಕಿ...",
            "Notifications" to "ಅಧಿಸೂಚನೆಗಳು",
            "Profile Information" to "ಪ್ರೊಫೈಲ್ ಮಾಹಿತಿ",
            "Full Name" to "ಪೂರ್ಣ ಹೆಸರು",
            "Select State" to "ರಾಜ್ಯ ಆಯ್ಕೆಮಾಡಿ",
            "Select Language" to "ಭಾಷೆ ಆಯ್ಕೆಮಾಡಿ",
            "Your Mobile Number (Verification & Updates)" to "ನಿಮ್ಮ ಮೊಬೈಲ್ ಸಂಖ್ಯೆ (ವೆರಿಫಿಕೇಶನ್ ಮತ್ತು ಅಪ್ಡೇಟ್ಸ್)",
            "Your Age in Years" to "ನಿಮ್ಮ ವಯಸ್ಸು ವರ್ಷಗಳಲ್ಲಿ",
            "Selected Category" to "ಆಯ್ಕೆ ಮಾಡಿದ ವರ್ಗ",
            "Create Profile" to "ಪ್ರೊಫೈಲ್ ರಚಿಸಿ",
            "Update Profile" to "ಪ್ರೊಫೈಲ್ ನವೀಕರಿಸಿ",
            "Access My Career Hub" to "ನನ್ನ ಉದ್ಯೋಗ ಕೇಂದ್ರ ಪ್ರವೇಶಿಸಿ",
            "Enter your 10-digit mobile number or Email to proceed" to "ಮುಂದುವರಿಯಲು ನಿಮ್ಮ 10 ಅಂಕಿಗಳ ಮೊಬೈಲ್ ಸಂಖ್ಯೆ ಅಥವಾ ಇಮೇಲ್ ನಮೂದಿಸಿ",
            "Official Calendars" to "ಅಧಿಕೃತ ಕ್ಯಾಲೆಂಡರ್‌ಗಳು",
            "All Notifications" to "ಎಲ್ಲಾ ಅಧಿಸೂಚನೆಗಳು",
            "Clear All" to "ಎಲ್ಲವನ್ನೂ ಅಳಿಸಿ",
            "Automatic Scanner Engine Active" to "ಸ್ವಯಂಚಾಲಿತ ಸ್ಕ್ಯಾನರ್ ಸಕ್ರಿಯ",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "ಅಧಿಕೃತ ಪರೀಕ್ಷಾ ಪೋರ್ಟಲ್‌ಗಳಿಂದ ಪ್ರತಿ ಗಂಟೆಗೆ ಪರಿಶೀಲಿಸಿದ ಮಾಹಿತಿ.",
            "Released:" to "ಬಿಡುგಡೆಯಾಗಿದೆ:",
            "View Calendars" to "ಕ್ಯಾಲೆಂಡರ್ ವೀಕ್ಷಿಸಿ",
            "Latest Jobs Tracker" to "ಉದ್ಯೋಗಗಳು ಟ್ರ್ಯಾಕರ್",
            "Profile & AI Coach" to "ಪ್ರೊಫೈಲ್ ಮತ್ತು AI ಕೋಚ್",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "ಆಯೋಗದ ಪ್ರವೇಶ ಪತ್ರ ವಿವರ ಪರಿಶೀಲಿಸಿ. ಹಾಲ್ ಟಿಕೆಟ್ ಡೌನ್ಲೋಡ್ ಮಾಡಿ.",
            "Verified Exam Hall Ticket" to "ಪರಿಶೀಲಿಸಲಾದ ಪರೀಕ್ಷಾ ಹಾಲ್ ಟಿಕೆಟ್",
            "Paper Scheduled Date:" to "ಪರೀಕ್ಷೆಯ ದಿನಾಂಕ:",
            "View Admit Cards" to "ಪ್ರವೇಶ ಪತ್ರ ವೀಕ್ಷಿಸಿ",
            "No announcements yet." to "ಯಾವುದೇ ಘೋಷಣೆಗಳಿಲ್ಲ.",
            "Active Jobs List" to "ಸಕ್ರಿಯ ಉದ್ಯೋಗಗಳ ಪಟ್ಟಿ",
            "Quick Alerts" to "ತ್ವರಿತ ಎಚ್ಚರಿಕೆಗಳು",
            "Government Jobs" to "ಸರ್ಕಾರಿ ಉದ್ಯೋಗಗಳು",
            "Enter Full Name" to "ಪೂರ್ಣ ಹೆಸರು ನಮೂದಿಸಿ",
            "Age (Yrs)" to "ವಯಸ್ಸು (ವರ್ಷಗಳು)",
            "Preferred Reading Language" to "ಓದುವ ಭಾಷೆ",
            "Verify & Access Hub" to "ಪರಿಶೀಲಿಸಿ ಮತ್ತು ಪ್ರವೇಶಿಸಿ",
            "Select Category Group" to "ವರ್ಗದ ಗುಂಪನ್ನು ಆರಿಸಿ"
        ),
        "Malayalam" to mapOf(
            "Home" to "ഹോം",
            "Jobs" to "ജോലികൾ",
            "Calendar" to "കലണ്ടർ",
            "Admit Cards" to "അഡ്മിറ്റ് കാർഡുകൾ",
            "Results" to "ഫലങ്ങൾ",
            "Coach/User" to "AI കോച്ച്",
            "CareerPlus AI" to "കരിയർപ്ലസ് AI",
            "Never Miss an Opportunity" to "ഒരു അവസരവും നഷ്ടപ്പെടുത്തരുത്",
            "Government Exam Calendars" to "സർക്കാർ പരീക്ഷാ കലണ്ടർ",
            "Admit Cards & Hall Tickets" to "അഡ്മിറ്റ് കാർഡുകളും പരീക്ഷാ വിവരങ്ങളും",
            "Results Declared" to "പ്രഖ്യാപിച്ച ഫലങ്ങൾ",
            "Apply Last Date" to "അപേക്ഷിക്കേണ്ട അവസാന തീയതി",
            "Salary" to "ശമ്പളം",
            "Age Limit" to "പ്രായപരിധി",
            "Qualification" to "യോഗ്യത",
            "State" to "സംസ്ഥാനം",
            "Official Website" to "ഔദ്യോഗിക വെബ്സൈറ്റ്",
            "Download Calendar" to "കലണ്ടർ ഡൗൺലോഡ്",
            "Download Admission Certificate (PDF)" to "അഡ്മിറ്റ് കാർഡ് ഡൗൺലോഡ് (PDF)",
            "Download Hall Ticket (PDF)" to "അഡ്മിറ്റ് കാർഡ് ഡൗൺലോഡ് (PDF)",
            "AI Coach" to "AI കോച്ച്",
            "Search Government Jobs..." to "സർക്കാർ ജോലികൾ തിരയുക...",
            "Ask AI Coach..." to "AI കോച്ചിനോട് ചോദിക്കുക...",
            "Active Jobs" to "സജീവ ജോലികൾ",
            "Latest Announced Results" to "ഏറ്റവും പുതിയ ഫലങ്ങൾ",
            "Check Merit List & Result PDF" to "മെറിറ്റ് ലിസ്റ്റും ഫലവും (PDF)",
            "Search Results..." to "ഫലങ്ങൾ തിരയുക...",
            "Notifications" to "അറിയിപ്പുകൾ",
            "Profile Information" to "പ്രൊഫൈൽ വിവരങ്ങൾ",
            "Full Name" to "പൂർണ്ണമായ പേര്",
            "Select State" to "സംസ്ഥാനം തിരഞ്ഞെടുക്കുക",
            "Select Language" to "ഭാഷ തിരഞ്ഞെടുക്കുക",
            "Your Mobile Number (Verification & Updates)" to "മൊബൈൽ നമ്പർ (സ്ഥിരീകരണവും അപ്‌ഡേറ്റുകളും)",
            "Your Age in Years" to "പ്രായം (വർഷത്തിൽ)",
            "Selected Category" to "തിരഞ്ഞെടുത്ത വിഭാഗം",
            "Create Profile" to "പ്രൊഫൈൽ നിർമ്മിക്കുക",
            "Update Profile" to "വിവരങ്ങൾ പുതുക്കുക",
            "Access My Career Hub" to "എന്റെ കരിയർ ഹബ് പ്രവേശിക്കുക",
            "Enter your 10-digit mobile number or Email to proceed" to "തുടരുന്നതിനായി നിങ്ങളുടെ 10 അക്ക മൊബൈൽ നമ്പറോ ഇമെയിലോ നൽകുക",
            "Official Calendars" to "ഔദ്യോഗിക കലണ്ടറുകൾ",
            "All Notifications" to "എല്ലാ അറിയിപ്പുകളും",
            "Clear All" to "എല്ലാം മായ്ക്കുക",
            "Automatic Scanner Engine Active" to "ഓട്ടോമാറ്റിക് സ്കാനർ സജീവം",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "ഔദ്യോഗിക പരീക്ഷാ പോർട്ടലുകളിൽ നിന്ന് നേരിട്ട് സാക്ഷ്യപ്പെടുത്തിയത്.",
            "Released:" to "പുറത്തിറങ്ങി:",
            "View Calendars" to "കലണ്ടറുകൾ കാണുക",
            "Latest Jobs Tracker" to "തൊഴിൽ വാർത്തകൾ",
            "Profile & AI Coach" to "പ്രൊഫൈലും AI കോച്ചും",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "കമ്മീഷനിൽ നിന്ന് വിവരങ്ങൾ സ്ഥിരീകരിക്കുക. ഹാൾ ടിക്കറ്റ് സൂക്ഷിക്കുക.",
            "Verified Exam Hall Ticket" to "സ്ഥിരീകരിച്ച പരീക്ഷ ഹാൾ ടിക്കറ്റ്",
            "Paper Scheduled Date:" to "പരീക്ഷാ തീയതി:",
            "View Admit Cards" to "അഡ്മിറ്റ് കാർഡുകൾ കാണുക",
            "No announcements yet." to "അറിയിപ്പുകൾ ഒന്നും തന്നെയില്ല.",
            "Active Jobs List" to "സജീവ ജോലികളുടെ പട്ടിക",
            "Quick Alerts" to "ദ്രുത അലേർട്ടുകൾ",
            "Government Jobs" to "സർക്കാർ ജോലികൾ",
            "Enter Full Name" to "പൂർണ്ണ നാമം നൽകുക",
            "Age (Yrs)" to "പ്രായം (വർഷം)",
            "Preferred Reading Language" to "ആഗ്രഹിക്കുന്ന ഭാഷ",
            "Verify & Access Hub" to "സ്ഥിരീകരിച്ചു പ്രവേശിക്കുക",
            "Select Category Group" to "വിഭാഗം തിരഞ്ഞെടുക്കുക"
        ),
        "Punjabi" to mapOf(
            "Home" to "ਹੋਮ",
            "Jobs" to "ਨੌਕਰੀਆਂ",
            "Calendar" to "ਕੈਲੰਡਰ",
            "Admit Cards" to "ਐਡਮਿਟ ਕਾਰਡ",
            "Results" to "ਨਤੀਜੇ",
            "Coach/User" to "AI ਕੋਚ",
            "CareerPlus AI" to "ਕਰੀਅਰਪਲੱਸ AI",
            "Never Miss an Opportunity" to "ਕੋਈ ਮੌਕਾ ਨਾ ਗੁਆਓ",
            "Government Exam Calendars" to "ਸਰਕਾਰੀ ਪ੍ਰੀਖਿਆ ਕੈਲੰਡਰ",
            "Admit Cards & Hall Tickets" to "ਐਡਮਿਟ ਕਾਰਡ ਅਤੇ ਪ੍ਰੀਖਿਆ ਵੇਰਵੇ",
            "Results Declared" to "ਐਲਾਨੇ ਨਤੀਜੇ",
            "Apply Last Date" to "ਅਪਲਾਈ ਕਰਨ ਦੀ ਆਖਰੀ ਮਿਤੀ",
            "Salary" to "ਤਨਖਾਹ",
            "Age Limit" to "ਉਮਰ ਸੀਮਾ",
            "Qualification" to "ਯੋਗਤਾ",
            "State" to "ਰਾਜ",
            "Official Website" to "ਅਧਿਕਾਰਤ ਵੈੱਬਸਾਈٹ",
            "Download Calendar" to "ਕੈਲੰਡਰ ਡਾਊਨਲੋਡ",
            "Download Admission Certificate (PDF)" to "ਐਡਮਿਟ ਕਾਰਡ ਡਾਊਨਲੋਡ (PDF)",
            "Download Hall Ticket (PDF)" to "ਐਡਮਿਟ ਕਾਰਡ ਡਾਊਨਲੋਡ (PDF)",
            "AI Coach" to "AI ਕੋਚ",
            "Search Government Jobs..." to "ਸਰਕਾਰੀ ਨੌਕਰੀਆਂ ਲੱਭੋ...",
            "Ask AI Coach..." to "AI ਕੋਚ ਨੂੰ ਪੁੱਛੋ...",
            "Active Jobs" to "ਸਰਗਰਮ ਨੌਕਰੀਆਂ",
            "Latest Announced Results" to "ਤਾਜ਼ਾ ਨਤੀਜੇ",
            "Check Merit List & Result PDF" to "ਮੈਰਿਟ ਸੂਚੀ ਅਤੇ ਨਤੀਜਾ (PDF)",
            "Search Results..." to "ਨਤੀਜੇ ਲੱਭੋ...",
            "Notifications" to "ਨੋਟੀਫਿਕੇਸ਼ਨ",
            "Profile Information" to "ਪ੍ਰੋਫਾਈਲ ਜਾਣਕਾਰੀ",
            "Full Name" to "ਪੂਰਾ ਨਾਮ",
            "Select State" to "ਰਾਜ ਚੁਣੋ",
            "Select Language" to "ਭਾਸ਼ਾ ਚੁਣੋ",
            "Your Mobile Number (Verification & Updates)" to "ਤੁਹਾਡਾ ਮੋਬਾਇਲ ਨੰਬਰ (ਵੈਰੀਫਿਕੇਸ਼ਨ ਅਤੇ ਅਪਡੇਟਸ)",
            "Your Age in Years" to "ਤੁਹਾਡੀ ਉਮਰ ਸਾਲਾਂ ਵਿੱਚ",
            "Selected Category" to "ਚੁਣੀ ਹੋਈ ਸ਼੍ਰੇਣੀ",
            "Create Profile" to "ਪ੍ਰੋਫਾਈਲ ਬਣਾਓ",
            "Update Profile" to "ਪ੍ਰੋਫਾਈਲ ਅੱਪਡੇਟ ਕਰੋ",
            "Access My Career Hub" to "ਮੇਰੇ ਕਰੀਅਰ ਹੱਬ ਵਿੱਚ ਜਾਓ",
            "Enter your 10-digit mobile number or Email to proceed" to "ਅੱਗੇ ਵਧਣ ਲਈ ਆਪਣਾ 10 ਅੰਕਾਂ ਦਾ ਮੋਬਾਈਲ ਨੰਬਰ ਜਾਂ ਈਮੇਲ ਦਰਜ ਕਰੋ",
            "Official Calendars" to "ਅਧਿਕਾਰਤ ਕੈਲੰਡਰ",
            "All Notifications" to "ਸਾਰੇ ਨੋਟੀਫਿਕੇਸ਼ਨ",
            "Clear All" to "ਸਭ ਸਾਫ਼ ਕਰੋ",
            "Automatic Scanner Engine Active" to "ਆਟੋਮੈਟਿਕ ਸਕੈਨਰ ਸਰਗਰਮ",
            "Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates." to "ਅਧਿਕਾਰਤ ਪ੍ਰੀਖਿਆ ਪੋਰਟਲ ਤੋਂ ਪ੍ਰਮਾਣਿਤ ਅੱਪਡੇਟ।",
            "Released:" to "ਜਾਰੀ ਹੋਇਆ:",
            "View Calendars" to "ਕੈਲੰਡਰ ਦੇਖੋ",
            "Latest Jobs Tracker" to "ਨੌਕਰੀਆਂ ਟ੍ਰੈਕਰ",
            "Profile & AI Coach" to "ਪ੍ਰੋਫਾਈਲ ਅਤੇ AI ਕੋਚ",
            "Verify details from direct service commissions. Download and keep copy of Hall Ticket." to "ਕਮਿਸ਼ਨ ਤੋਂ ਜਾਣਕਾਰੀ ਵੈਰੀਫਾਈ ਕਰੋ। ਹਾਲ ਟਿਕਟ ਡਾਊਨਲੋડ ਕਰੋ।",
            "Verified Exam Hall Ticket" to "ਵੈਰੀਫਾਈਡ ਪ੍ਰੀਖਿਆ ਹਾਲ ਟਿਕਟ",
            "Paper Scheduled Date:" to "ਪ੍ਰੀਖਿਆ ਦੀ ਮਿਤੀ:",
            "View Admit Cards" to "ਐਡਮਿਟ ਕਾਰਡ ਦੇਖੋ",
            "No announcements yet." to "ਹਾਲੇ ਕੋਈ ਐਲਾਨ ਨਹੀਂ ਹੋਇਆ।",
            "Active Jobs List" to "ਸਰਗਰਮ ਨੌਕਰੀਆਂ ਦੀ ਸੂਚੀ",
            "Quick Alerts" to "ਤੁਰੰਤ ਅਲਰਟ",
            "Government Jobs" to "ਸਰਕਾਰੀ ਨੌਕਰੀਆਂ",
            "Enter Full Name" to "ਪੂਰਾ ਨਾਮ ਦਰਜ ਕਰੋ",
            "Age (Yrs)" to "ਉਮਰ (ਸਾਲ)",
            "Preferred Reading Language" to "ਪੜ੍ਹਨ ਦੀ ਭਾਸ਼ਾ",
            "Verify & Access Hub" to "ਵੈਰੀਫਾਈ ਕਰੋ ਅਤੇ ਪ੍ਰਵੇਸ਼ ਕਰੋ",
            "Select Category Group" to "ਸ਼੍ਰੇਣੀ ਸਮੂਹ ਚੁਣੋ"
        )
    )

    fun translate(text: String, language: String): String {
        if (text.isBlank() || language == "English") return text

        val langMap = translations[language]
        if (langMap != null && langMap.containsKey(text)) {
            val result = langMap[text]
            if (result != null) return result
        }

        val cached = aiTranslationsCache[language]?.get(text)
        if (cached != null) return cached

        // Fallback to enqueuing background translation via Gemini AI
        enqueueTranslation(text, language)

        return text
    }
}
