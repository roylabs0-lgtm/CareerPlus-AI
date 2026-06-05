package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.viewmodel.CareerPlusViewModel
import com.example.util.AppLocalizer
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Constants for AdMob Configuration
object AdMobUnits {
    const val BANNER = "ca-app-pub-7428020944410359/1599378502"
    const val INTERSTITIAL = "ca-app-pub-7428020944410359/9286296836"
    const val REWARDED = "ca-app-pub-7428020944410359/6660133499"
    const val NATIVE = "ca-app-pub-7428020944410359/2940380338"
}

@Composable
fun MainAppScreen(viewModel: CareerPlusViewModel) {
    val context = LocalContext.current
    var isNotificationPanelOpen by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("HOME") } // HOME, JOBS, CALLS, STATS, CHAT
    
    // Manage full screen dialog overlays for simulated interstitial/rewarded ads
    var isSimulatedRewardedAdOpen by remember { mutableStateOf(false) }
    var rewardedAdCallback: (() -> Unit)? by remember { mutableStateOf(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // --- Flow Switcher ---
            Column(modifier = Modifier.fillMaxSize()) {
                // Persistent localized warning indicator if offline
                if (!viewModel.isInternetAvailable) {
                    val fallbackT = { key: String -> AppLocalizer.translate(key, viewModel.languageInput) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .testTag("offline_connection_banner"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Connection Warning Indicator",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fallbackT("No Internet Connection"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = fallbackT("Showing cached offline data. Unable to load fresh notifications."),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.checkInternetConnection()
                                    if (viewModel.isInternetAvailable) {
                                        Toast.makeText(context, "Network Connection restored!", Toast.LENGTH_SHORT).show()
                                        viewModel.triggerHourlyAutomationSync()
                                    } else {
                                        Toast.makeText(context, "Still offline. Please check your system settings.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(fallbackT("Retry"), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (viewModel.currentFlowState) {
                        "SPLASH" -> SplashScreen(viewModel = viewModel, onGetStarted = {
                            viewModel.currentFlowState = "LOGIN"
                        })
                        "LOGIN" -> LoginScreen(viewModel = viewModel)
                        "PROFILE_CREATION" -> ProfileCreationScreen(viewModel = viewModel)
                        "MAIN" -> {
                            MainTabbedScreen(
                                viewModel = viewModel,
                                activeTab = activeTab,
                                onTabSelected = { tab ->
                                    viewModel.checkAndTriggerTransitionAd("Tab: $tab")
                                    activeTab = tab
                                },
                                onOpenNotifications = {
                                    isNotificationPanelOpen = true
                                    viewModel.markNotificationsAsRead()
                                },
                                onWatchRewardedAd = { callback ->
                                    rewardedAdCallback = callback
                                    isSimulatedRewardedAdOpen = true
                                }
                            )
                        }
                    }
                }
            }

            // --- Real-time Notification Panel Slide Over Sheet ---
            if (isNotificationPanelOpen) {
                NotificationPanelSheet(
                    viewModel = viewModel,
                    onDismiss = { isNotificationPanelOpen = false }
                )
            }

            // --- SIMULATED INTERSTITIAL AD OVERLAY ---
            val activeInterstitial = viewModel.activeInterstitialAd
            if (activeInterstitial != null) {
                SimulatedInterstitialDialog(
                    actionTag = activeInterstitial,
                    onDismiss = {
                        viewModel.activeInterstitialAd = null
                    }
                )
            }

            // --- SIMULATED REWARDED AD OVERLAY ---
            if (isSimulatedRewardedAdOpen) {
                SimulatedRewardedAdDialog(
                    onAdCompleted = {
                        isSimulatedRewardedAdOpen = false
                        Toast.makeText(context, "Premium Reward Unlocked: +1 AI Consultation!", Toast.LENGTH_LONG).show()
                        rewardedAdCallback?.invoke()
                    },
                    onDismiss = {
                        isSimulatedRewardedAdOpen = false
                    }
                )
            }

            // --- OPTIMIZED ADVANCED WEBVIEW OVERLAY ---
            val activeWebUrl = viewModel.activeWebUrl
            if (!activeWebUrl.isNullOrBlank()) {
                GovernmentWebViewDialog(
                    url = activeWebUrl,
                    title = viewModel.activeWebTitle ?: "Official Government Portal",
                    onDismiss = {
                        viewModel.activeWebUrl = null
                        viewModel.activeWebTitle = null
                    }
                )
            }
        }
    }
}

// ==========================================
// 1. SPLASH SCREEN
// ==========================================
@Composable
fun SplashScreen(viewModel: CareerPlusViewModel, onGetStarted: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var expandedLangDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val languages = listOf("English", "Hindi", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Kannada", "Punjabi", "Malayalam")
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    LaunchedEffect(Unit) {
        delay(200)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RoyalNavy, DeepBlackBlue)
                )
            )
    ) {
        // Instant Language Selector in Top Right Corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 44.dp, end = 16.dp)
        ) {
            IconButton(
                onClick = { expandedLangDropdown = !expandedLangDropdown },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Select Language",
                    tint = GoldenSun
                )
            }
            DropdownMenu(
                expanded = expandedLangDropdown,
                onDismissRequest = { expandedLangDropdown = false }
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        leadingIcon = {
                            if (viewModel.languageInput == lang) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        text = { Text(lang) },
                        onClick = {
                            expandedLangDropdown = false
                            viewModel.updateLanguage(lang)
                            val feedbackStr = AppLocalizer.translate("System default language configured as:", lang) + " " + lang
                            Toast.makeText(context, feedbackStr, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.2f))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000)) + expandVertically(tween(1200))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Custom logo
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(GoldenSun.copy(alpha = 0.25f), Color.Transparent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.careerplus_logo_1780630880070),
                            contentDescription = "CareerPlus Brand Logo",
                            modifier = Modifier
                                .size(105.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(2.dp, GoldenSun.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "CareerPlus AI",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldenSun,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = t("Never Miss an Opportunity"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Text(
                        text = t("India's Premiere Government Jobs, Admit Cards, Results, notifications, and AI Coaching platform."),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .testTag("get_started_button"),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = t("Access My Career Hub"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = Color.Black)
                }
            }
            
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// ==========================================
// 2. LOGIN SCREEN
// ==========================================
@Composable
fun LoginScreen(viewModel: CareerPlusViewModel) {
    var contactInput by remember { mutableStateOf("") }
    var isPhoneType by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var expandedLangDropdown by remember { mutableStateOf(false) }

    // OTP Verification Gatekeeper States
    var showOtpScreen by remember { mutableStateOf(false) }
    var expectedOtp by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf("") }
    var isNotificationVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }
    val languages = listOf("English", "Hindi", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Kannada", "Punjabi", "Malayalam")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Corner Language selector for seamless UX prior to authentication
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            IconButton(
                onClick = { expandedLangDropdown = !expandedLangDropdown },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Select Language",
                    tint = GoldenSun
                )
            }
            DropdownMenu(
                expanded = expandedLangDropdown,
                onDismissRequest = { expandedLangDropdown = false }
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        leadingIcon = {
                            if (viewModel.languageInput == lang) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        text = { Text(lang) },
                        onClick = {
                            expandedLangDropdown = false
                            viewModel.updateLanguage(lang)
                            val feedbackStr = AppLocalizer.translate("System default language configured as:", lang) + " " + lang
                            Toast.makeText(context, feedbackStr, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // Brand Header minimal
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = t("Welcome to CareerPlus"),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(30.dp))

            if (!showOtpScreen) {
                // Credentials Input UI
                Text(
                    text = t("Log in to instantly monitor exam notifications and sync schedules"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tab toggler for Phone OTP vs Email Login
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { isPhoneType = true; contactInput = "" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPhoneType) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isPhoneType) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            elevation = null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(t("Phone OTP"))
                        }

                        Button(
                            onClick = { isPhoneType = false; contactInput = "" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isPhoneType) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (!isPhoneType) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            elevation = null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(t("Email Portal"))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sub Form Title
                Text(
                    text = if (isPhoneType) t("Mobile Verification Details") else t("Professional Email Authentication"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = contactInput,
                    onValueChange = {
                        contactInput = it
                        errorMessage = ""
                    },
                    label = { Text(if (isPhoneType) t("10-Digit Phone Number") else t("Candidate Email Address")) },
                    placeholder = { Text(if (isPhoneType) t("e.g., +91 90019 5XXXX") else t("e.g., rahul900195@gmail.com")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_input_field"),
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPhoneType) Icons.Default.SmartButton else Icons.Default.MarkEmailRead,
                            contentDescription = null
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPhoneType) KeyboardType.Phone else KeyboardType.Email
                    ),
                    singleLine = true,
                    isError = errorMessage.isNotBlank(),
                    colors = OutlinedTextFieldDefaults.colors()
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isPhoneType) {
                    Text(
                        text = t("A secure verification code (OTP) will be dispatched dynamically of 6 digits to verify this handset."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = {
                        if (contactInput.isBlank()) {
                            errorMessage = t("This authentication field cannot be blank.")
                            return@Button
                        }
                        if (isPhoneType && contactInput.length < 10) {
                            errorMessage = t("Please enter a valid 10-digit Indian Mobile parameter.")
                            return@Button
                        }
                        if (!isPhoneType && !contactInput.contains("@")) {
                            errorMessage = t("A complete email address including '@' is required to register.")
                            return@Button
                        }
                        
                        // Generate a random 6-digit OTP code dynamically
                        val randomCode = (100000 + (Math.random() * 900000).toInt()).toString()
                        expectedOtp = randomCode
                        otpInput = ""
                        otpError = ""
                        showOtpScreen = true
                        isNotificationVisible = true
                        
                        // Notify the candidate of the verification code
                        Toast.makeText(context, AppLocalizer.translate("Verification code sent! Code is:", viewModel.languageInput) + " " + randomCode, Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_button")
                ) {
                    Text(
                        text = if (isPhoneType) t("Fetch Security OTP") else t("Secure Instant Login"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                // OTP Verification Gatekeeper UI
                Text(
                    text = t("Enter Security Verification Code"),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isPhoneType) {
                        t("A 6-digit OTP SMS has been sent securely to: ") + contactInput
                    } else {
                        t("A 6-digit authentication token has been emailed securely to: ") + contactInput
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = otpInput,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            otpInput = it
                            otpError = ""
                        }
                    },
                    label = { Text(t("6-Digit OTP Code")) },
                    placeholder = { Text("e.g. 123456") },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp
                    ),
                    modifier = Modifier
                        .width(220.dp)
                        .testTag("otp_input_field"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = otpError.isNotBlank(),
                    colors = OutlinedTextFieldDefaults.colors()
                )

                if (otpError.isNotBlank()) {
                    Text(
                        text = otpError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = {
                        if (otpInput.length != 6) {
                            otpError = t("Please enter the complete 6-digit verification code.")
                            return@Button
                        }
                        if (otpInput != expectedOtp) {
                            otpError = t("Incorrect verification code. Please check code or try again.")
                            return@Button
                        }
                        
                        // Verification successful! Proceed with login check
                        viewModel.performLogin(contactInput, isPhoneType)
                        showOtpScreen = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("verify_otp_button")
                ) {
                    Text(
                        text = t("Verify & Login safely"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            showOtpScreen = false
                            otpInput = ""
                            otpError = ""
                            isNotificationVisible = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(t("Change Input"))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedButton(
                        onClick = {
                            otpInput = ""
                            otpError = ""
                            val randomCode = (100000 + (Math.random() * 900000).toInt()).toString()
                            expectedOtp = randomCode
                            isNotificationVisible = true
                            Toast.makeText(context, AppLocalizer.translate("Verification code resent! Code is:", viewModel.languageInput) + " " + randomCode, Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(t("Resend OTP"))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Floating Simulated SMS Message/Notification Banner
        if (showOtpScreen && expectedOtp.isNotBlank() && isNotificationVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 70.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, GoldenSun.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = "Simulated SMS",
                                    tint = GoldenSun,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = t("Simulated SMS Message"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = GoldenSun
                                )
                                Text(
                                    text = " • " + t("just now"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(
                                onClick = { isNotificationVisible = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isPhoneType) {
                                "CareerPlus Gateway: " + t("Your 6-Digit security OTP is ") + expectedOtp + ". " + t("Do not share this mock validation code with anyone.")
                            } else {
                                "Email Server: " + t("Your security Token is ") + expectedOtp + ". " + t("Use it to verify your registration immediately.")
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    otpInput = expectedOtp
                                    otpError = ""
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = GoldenSun)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(t("Autofill Code"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. SHORT PROFILE CREATION SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCreationScreen(viewModel: CareerPlusViewModel) {
    val context = LocalContext.current
    val statesList = listOf("Rajasthan", "Uttar Pradesh", "Central / National Domain", "Delhi NCR", "Bihar", "Madhya Pradesh", "Maharashtra", "Karnataka")
    val qualificationsList = listOf("10th Pass", "12th Pass", "Graduation", "Post Graduation", "Other")
    val categoriesList = listOf("General", "OBC (Non-Creamy Ledger)", "SC (Scheduled Caste)", "ST (Scheduled Tribe)", "EWS (Economically Weaker Section)")
    val languagesList = listOf("English", "Hindi", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Kannada", "Punjabi", "Malayalam")

    var expandedState by remember { mutableStateOf(false) }
    var expandedQual by remember { mutableStateOf(false) }
    var expandedCat by remember { mutableStateOf(false) }
    var expandedLang by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    Scaffold(
        topBar = {
            OptInComponentVisuals {
                CenterAlignedTopAppBar(
                    title = { Text(t("Profile Information"), fontWeight = FontWeight.Bold, color = GoldenSun) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = RoyalNavy, titleContentColor = GoldenSun)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                Text(
                    text = t("Welcome to CareerPlus!"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = t("Configure your qualifications, location, and category parameters so our AI systems can customize relevant alerts dynamically."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Name Box
                Text(t("Your Full Name (As in Matriculation Sheet)"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(
                    value = viewModel.fullNameInput,
                    onValueChange = { viewModel.fullNameInput = it },
                    placeholder = { Text(t("e.g. Rahul Sharma")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp)
                        .testTag("profile_name_field"),
                    leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
                    singleLine = true
                )

                // Mobile Number Box
                Text(t("Your Mobile Number (Verification & Updates)"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(
                    value = viewModel.mobileNoInput,
                    onValueChange = { viewModel.mobileNoInput = it },
                    placeholder = { Text(t("e.g. 9876543210")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp)
                        .testTag("profile_mobile_field"),
                    leadingIcon = { Icon(imageVector = Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                // Age input
                Text(t("Your Age in Years (Verification Criteria)"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(
                    value = viewModel.ageInput,
                    onValueChange = { viewModel.ageInput = it },
                    placeholder = { Text(t("e.g. 23")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    leadingIcon = { Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                // Resident Domicile State selection
                Text(t("Domicile State Selection"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)) {
                    OutlinedTextField(
                        value = t(viewModel.stateInput),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expandedState = !expandedState }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expandedState,
                        onDismissRequest = { expandedState = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        statesList.forEach { state ->
                            DropdownMenuItem(
                                text = { Text(t(state)) },
                                onClick = {
                                    viewModel.stateInput = state
                                    expandedState = false
                                }
                            )
                        }
                    }
                }

                // Qualification Dynamic Selector
                Text(t("Highest Qualification Profile"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)) {
                    OutlinedTextField(
                        value = t(viewModel.qualificationInput),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expandedQual = !expandedQual }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expandedQual,
                        onDismissRequest = { expandedQual = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        qualificationsList.forEach { qual ->
                            DropdownMenuItem(
                                text = { Text(t(qual)) },
                                onClick = {
                                    viewModel.qualificationInput = qual
                                    expandedQual = false
                                }
                            )
                        }
                    }
                }

                // Category selector
                Text(t("Exam Category Allocation"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)) {
                    OutlinedTextField(
                        value = t(viewModel.categoryInput),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expandedCat = !expandedCat }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expandedCat,
                        onDismissRequest = { expandedCat = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        categoriesList.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(t(cat)) },
                                onClick = {
                                    viewModel.categoryInput = cat
                                    expandedCat = false
                                }
                            )
                        }
                    }
                }

                // Preferred App/Mock Language Selector
                Text(t("Preferred Reading Language"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)) {
                    OutlinedTextField(
                        value = viewModel.languageInput,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expandedLang = !expandedLang }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expandedLang,
                        onDismissRequest = { expandedLang = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        languagesList.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang) },
                                onClick = {
                                    viewModel.updateLanguage(lang)
                                    expandedLang = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (viewModel.fullNameInput.isNotBlank() && viewModel.ageInput.isNotBlank()) {
                            viewModel.saveUserProfile()
                        } else {
                            Toast.makeText(context, t("Full Name & Age parameters are required."), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_profile_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black)
                ) {
                    Text(t("Assemble Profile Preferences"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ==========================================
// 4. MAIN NAVIGATION FLOWS
// ==========================================
@Composable
fun MainTabbedScreen(
    viewModel: CareerPlusViewModel,
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onWatchRewardedAd: (() -> Unit) -> Unit
) {
    Scaffold(
        topBar = {
            FixedTopBar(
                viewModel = viewModel,
                onOpenNotifications = onOpenNotifications,
                onOpenProCheckout = {
                    onTabSelected("PROFILE")
                }
            )
        },
        bottomBar = {
            FixedBottomNavigation(
                activeTab = activeTab,
                viewModel = viewModel,
                onTabSelected = onTabSelected
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            
            // Central Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    "HOME" -> HomeScreen(viewModel = viewModel, onNavigateToTab = onTabSelected)
                    "JOBS" -> JobsListScreen(viewModel = viewModel)
                    "CARDS" -> AdmitCardsScreen(viewModel = viewModel)
                    "RESULTS" -> ResultsListScreen(viewModel = viewModel)
                    "PROFILE" -> ProfileCoachScreen(viewModel = viewModel, onNavigateToTab = onTabSelected, onWatchRewardedAd = onWatchRewardedAd)
                }
            }

            // Real-time AdMob Banner display (Controlled dynamically by isPro state)
            AdMobBanner(
                adUnitId = AdMobUnits.BANNER,
                isPro = viewModel.currentUserProfile?.isPro == true,
                screenContext = activeTab
            )
        }
    }
}

// ==========================================
// FIXED TOP BAR
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedTopBar(
    viewModel: CareerPlusViewModel,
    onOpenNotifications: () -> Unit,
    onOpenProCheckout: () -> Unit
) {
    var expandedLangDropdown by remember { mutableStateOf(false) }
    val profile = viewModel.currentUserProfile
    val context = LocalContext.current

    val languages = listOf("English", "Hindi", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Kannada", "Punjabi", "Malayalam")

    OptInComponentVisuals {
        val trg = viewModel.aiTranslationRefreshTrigger
        val t = { text: String -> 
            val d = trg
            AppLocalizer.translate(text, viewModel.languageInput)
        }
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = RoyalNavy,
                titleContentColor = GoldenSun
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = GoldenSun,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "CareerPlus AI",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = GoldenSun
                        )
                        Text(
                            text = t("Never Miss an Opportunity"),
                            fontSize = 10.sp,
                            color = GoldenSun.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            actions = {
                // Get Pro Tonal Badge / Button
                if (profile?.isPro == true) {
                    IconButton(onClick = onOpenProCheckout) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "CareerPlus Pro Active",
                            tint = GoldenSun,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenProCheckout,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .padding(end = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(t("Get PRO"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                // Global Language dropdown selector icon
                Box {
                    IconButton(onClick = { expandedLangDropdown = !expandedLangDropdown }) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Select Global Preferred Language",
                            tint = GoldenSun
                        )
                    }
                    DropdownMenu(
                        expanded = expandedLangDropdown,
                        onDismissRequest = { expandedLangDropdown = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    if (viewModel.languageInput == lang) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                text = { Text(lang) },
                                onClick = {
                                    expandedLangDropdown = false
                                    viewModel.updateLanguage(lang)
                                    val feedbackStr = AppLocalizer.translate("System default language configured as:", lang) + " " + lang
                                    Toast.makeText(context, feedbackStr, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Bell / Notification Panel Icon with dynamic counter badges
                val activeNotifications by viewModel.allNotifications.collectAsState()
                val hasUnread by viewModel.hasUnreadNotifications.collectAsState()
                Box {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notification Stream Reminders Center",
                            tint = GoldenSun
                        )
                    }
                    if (hasUnread && activeNotifications.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color.Red, CircleShape)
                                .align(Alignment.TopEnd)
                                .offset(x = (-4).dp, y = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeNotifications.size.toString(),
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        )
    }
}

// ==========================================
// FIXED BOTTOM NAVIGATION
// ==========================================
@Composable
fun FixedBottomNavigation(
    activeTab: String,
    viewModel: CareerPlusViewModel,
    onTabSelected: (String) -> Unit
) {
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }
    NavigationBar(
        containerColor = RoyalNavy,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = activeTab == "HOME",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoyalNavy,
                selectedTextColor = GoldenSun,
                indicatorColor = GoldenSun,
                unselectedIconColor = Color.White.copy(0.6f),
                unselectedTextColor = Color.White.copy(0.6f)
            ),
            onClick = { onTabSelected("HOME") },
            icon = { Icon(imageVector = if (activeTab == "HOME") Icons.Default.Home else Icons.Outlined.Home, contentDescription = "Home Hub") },
            label = { Text(t("Home"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )

        NavigationBarItem(
            selected = activeTab == "JOBS",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoyalNavy,
                selectedTextColor = GoldenSun,
                indicatorColor = GoldenSun,
                unselectedIconColor = Color.White.copy(0.6f),
                unselectedTextColor = Color.White.copy(0.6f)
            ),
            onClick = { onTabSelected("JOBS") },
            icon = { Icon(imageVector = if (activeTab == "JOBS") Icons.Default.Work else Icons.Outlined.WorkOutline, contentDescription = "Jobs Grid") },
            label = { Text(t("Jobs"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )

        NavigationBarItem(
            selected = activeTab == "CARDS",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoyalNavy,
                selectedTextColor = GoldenSun,
                indicatorColor = GoldenSun,
                unselectedIconColor = Color.White.copy(0.6f),
                unselectedTextColor = Color.White.copy(0.6f)
            ),
            onClick = { onTabSelected("CARDS") },
            icon = { Icon(imageVector = if (activeTab == "CARDS") Icons.Default.ConfirmationNumber else Icons.Outlined.ConfirmationNumber, contentDescription = "Exam Calendar & Admit Cards") },
            label = { Text(t("Admit Cards"), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        )

        NavigationBarItem(
            selected = activeTab == "RESULTS",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoyalNavy,
                selectedTextColor = GoldenSun,
                indicatorColor = GoldenSun,
                unselectedIconColor = Color.White.copy(0.6f),
                unselectedTextColor = Color.White.copy(0.6f)
            ),
            onClick = { onTabSelected("RESULTS") },
            icon = { Icon(imageVector = if (activeTab == "RESULTS") Icons.Default.EmojiEvents else Icons.Outlined.EmojiEvents, contentDescription = "Announced Results") },
            label = { Text(t("Results"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )

        NavigationBarItem(
            selected = activeTab == "PROFILE",
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoyalNavy,
                selectedTextColor = GoldenSun,
                indicatorColor = GoldenSun,
                unselectedIconColor = Color.White.copy(0.6f),
                unselectedTextColor = Color.White.copy(0.6f)
            ),
            onClick = { onTabSelected("PROFILE") },
            icon = { Icon(imageVector = if (activeTab == "PROFILE") Icons.Default.ManageAccounts else Icons.Outlined.ManageAccounts, contentDescription = "Settings AI Coach") },
            label = { Text(t("Coach/User"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )
    }
}

// ==========================================
// DETAILED HOME PORTAL PAGE
// ==========================================
@Composable
fun HomeScreen(viewModel: CareerPlusViewModel, onNavigateToTab: (String) -> Unit) {
    val jobs by viewModel.allJobs.collectAsState()
    val cards by viewModel.allAdmitCards.collectAsState()
    val results by viewModel.allResults.collectAsState()
    val savedIds by viewModel.savedJobIds.collectAsState()
    
    val profile = viewModel.currentUserProfile
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        
        // --- Hourly Synchronizer Board ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("Automatic Updates Engine"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = viewModel.lastSyncLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.selectedCategoryFilter = "Bookmarked 🔖"
                                onNavigateToTab("JOBS")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Bookmarked Vacancies",
                                tint = if (savedIds.isNotEmpty()) GoldenSun else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            )
                        }
                        
                        if (viewModel.isSynchronizing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.1.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.triggerHourlyAutomationSync()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync from official portals",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LockClock, contentDescription = null, sizeX = 14.dp, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = t("Synchronized hourly from SSC, UPSC, Railway, UPPSC & RPSC"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- Custom Domicile & Eligibility Recommendation Area ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(GoldenSun.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = GoldenSun)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("Matching Recommendations Map"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${t("Viewing:")} ${if (profile != null) t(profile.qualification) else t("UnderGraduate")} ${t("vacancies located in")} ${if (profile != null) t(profile.state) else t("All States")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 1. Recommended / Active Jobs ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("Premium Active recruitments"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { onNavigateToTab("JOBS") }) {
                Text(t("View All") + " (${jobs.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }
        
        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t("Indexing local feeds..."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Display first 3 top matching jobs on Dashboard
            jobs.take(3).forEach { job ->
                val isSaved = savedIds.any { it.id == job.id }
                DashboardJobCard(
                    job = job,
                    isSaved = isSaved,
                    languageInput = viewModel.languageInput,
                    onToggleSave = { viewModel.toggleJobSave(job) },
                    onSelect = { onNavigateToTab("JOBS") }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Injected Native Ad (Visible if isPro == false) ---
        AdMobNativeListingAd(
            adUnitId = AdMobUnits.NATIVE,
            isPro = viewModel.currentUserProfile?.isPro == true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. Upcoming Admit Cards Block ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("Active Hall Tickets"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { onNavigateToTab("CARDS") }) {
                Text(t("View Calendars"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }

        cards.take(2).forEach { card ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(card.examName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(t("Exam Date Scheduled:") + " ${card.examDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            viewModel.activeWebUrl = card.downloadLink
                            viewModel.activeWebTitle = card.examName + " - " + t("Admit Card Portal")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(t("Get"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Latest Announced Results Block ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("Results Declared"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { onNavigateToTab("RESULTS") }) {
                Text(t("Check Scorecards"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }

        results.take(2).forEach { result ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = GoldenSun)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(result.examName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(t("Announced:") + " ${result.resultDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            viewModel.activeWebUrl = result.resultLink
                            viewModel.activeWebTitle = result.examName + " - " + t("Merit List & Result Portal")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(t("Check"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}

// Custom Icon size adapter
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, sizeX: androidx.compose.ui.unit.Dp, tint: Color) {
    Box(modifier = Modifier.size(sizeX)) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun DashboardJobCard(
    job: JobPost,
    isSaved: Boolean,
    languageInput: String,
    onToggleSave: () -> Unit,
    onSelect: () -> Unit
) {
    val t = { text: String -> AppLocalizer.translate(text, languageInput) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified posting",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = t("VERIFIED VACANCY"),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = job.categoryGroup.uppercase(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconButton(onClick = onToggleSave, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save job preference",
                        tint = if (isSaved) GoldenSun else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Organization Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = "Organization",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${t("Organization:")} ${job.organization} • ${job.department}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Technical Requirements (e.g., Qualifications)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${t("Required Qualification:")} ${job.qualification}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = t("Last Date to Apply:"),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = job.lastDate,
                        fontSize = 12.sp,
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Button(
                    onClick = { onSelect() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = null, sizeX = 14.dp, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("Apply Online"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}

// ==========================================
// JOBS GRID SCREEN WITH ACCORDIONS / DETAIL MODAL
// ==========================================
@Composable
fun JobsListScreen(viewModel: CareerPlusViewModel) {
    val jobs by viewModel.allJobs.collectAsState()
    val savedIds by viewModel.savedJobIds.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val selectedCategoryFilter = viewModel.selectedCategoryFilter

    var activeDetailJob by remember { mutableStateOf<JobPost?>(null) }

    val categories = listOf("All", "Bookmarked 🔖", "Central Government", "Rajasthan", "Uttar Pradesh")
    val listState = rememberLazyListState()

    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    val filteredJobs = remember(jobs, searchQuery, selectedCategoryFilter, savedIds) {
        jobs.filter { job ->
            val matchesSearch = job.title.contains(searchQuery, ignoreCase = true) ||
                    job.organization.contains(searchQuery, ignoreCase = true) ||
                    job.qualification.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = when (selectedCategoryFilter) {
                "All" -> true
                "Bookmarked 🔖" -> savedIds.any { it.id == job.id }
                else -> job.categoryGroup == selectedCategoryFilter
            }
            matchesSearch && matchesCategory
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(t("Search by SSC, RPSC, Railway, UPPSC..."), fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("job_search_bar"),
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Category Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = {
                                viewModel.selectedCategoryFilter = cat
                            },
                            label = { Text(t(cat), fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredJobs.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Inbox, contentDescription = null, sizeX = 56.dp, tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(t("No recruiting announcements found."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(filteredJobs.size) { index ->
                        val job = filteredJobs[index]
                        val isSaved = savedIds.any { it.id == job.id }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.checkAndTriggerTransitionAd("Search: Open Job ID ${job.id}")
                                    activeDetailJob = job
                                    viewModel.clearTranslationCache()
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Verified,
                                                        contentDescription = "Verified posting",
                                                        modifier = Modifier.size(11.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = t("VERIFIED VACANCY"),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.width(6.dp))
                                            
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = job.categoryGroup.uppercase(),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = job.title,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { viewModel.toggleJobSave(job) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Save vacancy",
                                            tint = if (isSaved) GoldenSun else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 1. Organization Row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = "Organization",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${t("Organization:")} ${job.organization} • ${job.department}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // 2. Technical Requirements
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, sizeX = 14.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${t("Required Qualification:")} ${job.qualification}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = t("Last Date to Apply:"),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = job.lastDate,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE53935),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.checkAndTriggerTransitionAd("Job Apply: ${job.title}")
                                            viewModel.activeWebUrl = job.applyLink
                                            viewModel.activeWebTitle = job.title + " - " + t("Apply Online")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.Launch, contentDescription = null, sizeX = 14.dp, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(t("Apply Online"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }

                        // Inject dynamic Native Ad on index 2 and index 5 (Frequency constraints)
                        if (index == 1 || index == 4) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AdMobNativeListingAd(
                                adUnitId = AdMobUnits.NATIVE,
                                isPro = viewModel.currentUserProfile?.isPro == true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }

            // --- JOB DETAILED VIEW BOTTOM COMPONENT ---
            val jobSelected = activeDetailJob
            if (jobSelected != null) {
                JobDetailDialog(
                    viewModel = viewModel,
                    job = jobSelected,
                    onDismiss = {
                        activeDetailJob = null
                        viewModel.clearTranslationCache()
                    }
                )
            }
        }
    }
}

// ==========================================
// JOB DETAIL DIALOG LAYER
// ==========================================
@Composable
fun JobDetailDialog(
    viewModel: CareerPlusViewModel,
    job: JobPost,
    onDismiss: () -> Unit
) {
    val languages = listOf("English", "Hindi", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Kannada", "Punjabi", "Malayalam")
    var selectedTransLang by remember { mutableStateOf("Hindi") }
    val cacheText by viewModel.translatedDetailsCache
    val context = LocalContext.current
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Details, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(t("Government Job Specifications"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close detailed sheet", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(t("Department:") + " " + job.department, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Multi-Language Translation Row
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(t("Multilingual Reading Panel"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(t("Simulative automatic translator"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                Box {
                                    var dropTrans by remember { mutableStateOf(false) }
                                    TextButton(onClick = { dropTrans = true }) {
                                        Text("$selectedTransLang ▼", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    DropdownMenu(
                                        expanded = dropTrans,
                                        onDismissRequest = { dropTrans = false }
                                    ) {
                                        languages.forEach { lang ->
                                            DropdownMenuItem(
                                                text = { Text(lang, fontSize = 11.sp) },
                                                onClick = {
                                                    selectedTransLang = lang
                                                    dropTrans = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    viewModel.translateJobContent(job, selectedTransLang)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black)
                            ) {
                                if (viewModel.isTranslating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(t("Generating Summary...") + " ($selectedTransLang)", fontSize = 11.sp, color = Color.Black)
                                } else {
                                    Text(t("Read In ") + selectedTransLang + " " + t("(AI Translation)"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            // Cache Text Display if exists
                            if (cacheText != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .background(
                                            MaterialTheme.colorScheme.background,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(t("AI localized translation summary"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        Text(
                                            t("Clear [x]"),
                                            fontSize = 10.sp,
                                            modifier = Modifier.clickable { viewModel.clearTranslationCache() },
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = cacheText!!,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Detail Key value pairs
                    DetailRow(t("Organization Code"), job.organization)
                    DetailRow(t("Vacancies Count"), job.vacancyCount)
                    DetailRow(t("Basic Initial Salary"), job.salary)
                    DetailRow(t("Education Mandate"), job.qualification)
                    DetailRow(t("Application Fees"), job.applicationFees)
                    DetailRow(t("Age Eligibility Bounds"), job.ageLimit)
                    DetailRow(t("Presents Starts Date"), job.startDate)
                    DetailRow(t("Closure Submit Date"), job.lastDate)
                    DetailRow("Correction Window", job.correctionDate)
                    DetailRow("Scheduled examDate", job.examDate)
                    DetailRow("Expected Admit", job.admitCardDate)
                    DetailRow("Expected Result", job.resultDate)

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Official Verification Guidelines", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "CareerPlus AI always indexes data straight from formal government gazettes. Verify credentials with the links mapped below.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Call to actions layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.activeWebUrl = job.officialWebsite
                            viewModel.activeWebTitle = job.organization + " - " + t("Official Site")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Official Site", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.activeWebUrl = job.applyLink
                            viewModel.activeWebTitle = job.title + " - " + t("Apply Online")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Apply Online", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(key: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = key, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

// ==========================================
// ADMIT CARDS FEEDS PAGE
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmitCardsScreen(viewModel: CareerPlusViewModel) {
    val inviteCards by viewModel.allAdmitCards.collectAsState()
    val calendars by viewModel.allCalendars.collectAsState()
    val context = LocalContext.current
    var selectedCalendarTab by remember { mutableStateOf(0) } // 0 = Admit Cards, 1 = Official Calendars

    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (selectedCalendarTab == 0) t("Admit Cards & Hall Tickets") else t("Government Exam Calendars"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (selectedCalendarTab == 0) t("Get direct verified call certificates for scheduled papers") else t("Auto-crawled genuine schedulers from official agencies"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Premium Dark Tab Row
        TabRow(
            selectedTabIndex = selectedCalendarTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            Tab(
                selected = selectedCalendarTab == 0,
                onClick = { selectedCalendarTab = 0 },
                text = { Text(t("Admit Cards"), fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(imageVector = Icons.Default.ConfirmationNumber, contentDescription = null, sizeX = 18.dp, tint = if (selectedCalendarTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            Tab(
                selected = selectedCalendarTab == 1,
                onClick = { selectedCalendarTab = 1 },
                text = { Text(t("Official Calendars"), fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, sizeX = 18.dp, tint = if (selectedCalendarTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedCalendarTab == 0) {
            // ADMIT CARDS TAB SELECTED (index 0)
            if (inviteCards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t("Retrieving admit lists."))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(inviteCards) { card ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = t("Verified Exam Hall Ticket").uppercase(),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = card.examName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${t("Released:")} ${card.releaseDate}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = t("Verify details from direct service commissions. Download and keep copy of Hall Ticket."),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(t("Paper Scheduled Date:"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(card.examDate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            viewModel.checkAndTriggerTransitionAd("Admit Card Download: ${card.examName}")
                                            viewModel.activeWebUrl = card.downloadLink
                                            viewModel.activeWebTitle = card.examName + " - " + t("Admit Card Portal")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.Download, contentDescription = null, sizeX = 14.dp, tint = Color.Black)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(t("Download Hall Ticket (PDF)"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // OFFICIAL CALENDARS TAB SELECTED (index 1)
            // INFO BANNER REGARDING WEB-CRAWLER AUTOMATION
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = "Automation Crawler",
                        sizeX = 24.dp,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = t("Automatic Scanner Engine Active"),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = t("Direct content verified hourly from official exam portals ssc.gov.in, upsc.gov.in, and RRB. Fully genuine government updates."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (calendars.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(calendars) { event ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = event.organization.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = event.examName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = event.status,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(t("Notification"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(event.officialNotificationDate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(t("Application Phase"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${event.applyStartDate} to ${event.applyLastDate}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(t("Exam Date"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(event.examScheduledDate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.checkAndTriggerTransitionAd("Official Calendar Web Visit: ${event.examName}")
                                            viewModel.activeWebUrl = event.sourceWebsite
                                            viewModel.activeWebTitle = event.examName + " - " + t("Official Site")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(t("Official Website"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.checkAndTriggerTransitionAd("Official Calendar PDF Download: ${event.examName}")
                                            viewModel.activeWebUrl = event.officialCalendarPdfLink
                                            viewModel.activeWebTitle = event.examName + " - " + t("Official Calendar PDF")
                                        },
                                        modifier = Modifier.weight(1.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Description, contentDescription = null, sizeX = 14.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(t("Download Calendar"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// RESULTS ANNOUNCEMENT FEEDS PAGE
// ==========================================
@Composable
fun ResultsListScreen(viewModel: CareerPlusViewModel) {
    val results by viewModel.allResults.collectAsState()
    val context = LocalContext.current
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(t("Latest Announced Results"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Text(t("Check qualifications and direct cutoff charts straight from officials"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(14.dp))

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(t("No announcements yet."))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(results) { res ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = res.examName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(res.resultDate, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    viewModel.checkAndTriggerTransitionAd("Result Sheet Visit: ${res.examName}")
                                    viewModel.activeWebUrl = res.resultLink
                                    viewModel.activeWebTitle = res.examName + " - " + t("Merit List & Result Portal")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Launch, contentDescription = null, sizeX = 14.dp, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(t("Check Merit List & Result PDF"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PROFILE, PREMIUM CHECKOUT & GEMINI AI COACH
// ==========================================
@Composable
fun ProfileCoachScreen(
    viewModel: CareerPlusViewModel,
    onNavigateToTab: (String) -> Unit,
    onWatchRewardedAd: (() -> Unit) -> Unit
) {
    val profile = viewModel.currentUserProfile
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showContactForm by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var activeContactActionName by remember { mutableStateOf("Contact Support") }

    val scrollState = rememberScrollState()
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        
        // --- 1. Top profile summary block ---
        Card(
            colors = CardDefaults.cardColors(containerColor = RoyalNavy),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(GoldenSun, strokeCircle()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile?.fullName?.take(2)?.uppercase() ?: "CP",
                            style = MaterialTheme.typography.titleLarge.copy(color = Color.Black, fontWeight = FontWeight.ExtraBold)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = profile?.fullName ?: t("Guest Candidate"),
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = profile?.contact ?: t("No secure log details"),
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(0.6f))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("Status: ") + (if (profile?.isPro == true) t("PRO MEMBER") else t("FREE TIER")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile?.isPro == true) GoldenSun else Color.White.copy(0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (profile?.isPro == true) {
                        Text(t("Active (Auto Restore Logs)"), fontSize = 10.sp, color = GoldenSun)
                    } else {
                        Button(
                            onClick = { viewModel.purchaseProSubscription() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(t("Upgrade Pro @ ₹49"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showEditProfileDialog = true },
                    border = BorderStroke(1.dp, GoldenSun.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldenSun),
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit profile",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t("Edit Profile Preferences"),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Bookmarked / Tracked Vacancies Card ---
        val savedIds by viewModel.savedJobIds.collectAsState()
        val savedCount = savedIds.size
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (savedCount > 0) GoldenSun.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (savedCount > 0) GoldenSun.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Bookmarks Tracker",
                            tint = if (savedCount > 0) GoldenSun else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = t("Bookmarked Vacancies"),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (savedCount > 0) 
                                "${t("Tracking")} $savedCount ${t("saved vacancy notifications offline.")}" 
                            else 
                                t("No vacancies bookmarked yet. Tap bookmark on feed."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = {
                        viewModel.selectedCategoryFilter = "Bookmarked 🔖"
                        onNavigateToTab("JOBS")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (savedCount > 0) GoldenSun else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (savedCount > 0) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(t("View 🔖"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (savedCount > 0) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 2. Subscription Upgrade Sandbox ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            ),
            border = BorderStroke(1.dp, GoldenSun),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("CareerPlus AI Pro Active"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = t("Developer Sandbox Mode (Free Pre-release Session)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.purchaseProSubscription() },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(t("Sync Status"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. PREMIUM GEMINI AI COACH COMPONENT (Subscription Locked) ---
        Text(t("AI Coach Pro Consultation"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Text(t("Formulate custom study paths, solve eligibility syllabus, powered by Gemini 3.5"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(8.dp))

        // Observe and utilize PRO_MODE_ENABLED to bypass the paywall gate entirely
        val isProUnlocked = profile?.isPro == true || com.example.viewmodel.CareerPlusViewModel.PRO_MODE_ENABLED
        val chatClipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

        if (!isProUnlocked) {
            // Free Tier locker (Note: Hidden by default when PRO_MODE_ENABLED is true)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, sizeX = 36.dp, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(t("Gemini AI Career Counselor Locked"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = t("Counseling requires active CareerPlus Pro subscription. Alternatively, earn 1 free query right now by watching a brief partner rewarded video ad."),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onWatchRewardedAd {
                                    // Temporarily upgrade pro to allow 1 chat response
                                    viewModel.purchaseProSubscription()
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Watch Video Ad"), fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.purchaseProSubscription() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black)
                        ) {
                            Text(t("Get Pro"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        } else {
            // Unlocked AI coach chatbot terminal with premium features
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GoldenSun.copy(0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Chat header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RoyalNavy)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Green, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(t("Gemini Career Counselor Online"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        
                        // Tiny Pro Status Indicator Badge
                        Box(
                            modifier = Modifier
                                .background(GoldenSun, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(t("PRO STATUS UNLOCKED"), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        }
                    }

                    // Chat Scroll lists
                    Box(modifier = Modifier.weight(1f)) {
                        if (viewModel.chatHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(t("Ask any query! e.g., 'Make a 60-day syllabus schedule for General Math SSC CGL'"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                            }
                        } else {
                            val chatScrollState = rememberScrollState()
                            LaunchedEffect(viewModel.chatHistory.size, viewModel.isChatLoading) {
                                chatScrollState.animateScrollTo(chatScrollState.maxValue)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .verticalScroll(chatScrollState),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                viewModel.chatHistory.forEach { messagePair ->
                                    val (msg, isUser) = messagePair
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .padding(10.dp)
                                                .widthIn(max = 250.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = msg, 
                                                    fontSize = 11.sp, 
                                                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                // Message Timestamp
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    Text(
                                                        text = "Just now",
                                                        fontSize = 8.sp,
                                                        color = (if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Quick Action Bar for counselor responses
                                        if (!isUser) {
                                            Row(
                                                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // COPY ICON BUTTON
                                                TextButton(
                                                    onClick = {
                                                        chatClipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg))
                                                        viewModel.showSecurityToastMessage = "Roadmap copied to clipboard!"
                                                    },
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.height(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy, 
                                                        contentDescription = "Copy Response Text",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(t("Copy"), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }

                                                // REGENERATE ICON BUTTON
                                                TextButton(
                                                    onClick = { viewModel.regenerateLastResponse() },
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.height(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh, 
                                                        contentDescription = "Regenerate Guidance",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(t("Regenerate"), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Typing Indicator representation when AI response is downloading
                                if (viewModel.isChatLoading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = t("CareerCoach is formulating educational roadmap..."),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Input slot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = viewModel.chatMessageInput,
                            onValueChange = { viewModel.chatMessageInput = it },
                            placeholder = { Text(t("Ask Guidance..."), fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("coach_chat_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { viewModel.sendChatMessage() },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(36.dp)
                        ) {
                            if (viewModel.isChatLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Send, contentDescription = "Send advice query", tint = Color.White, sizeX = 16.dp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. Contact Us support block ---
        Text(t("Applicant Helplines"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        Text(t("Connect dynamically with CareerPlus developers regarding issues or features."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t("Support Email: roylabs0@gmail.com"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            activeContactActionName = "Contact Support"
                            showContactForm = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null, sizeX = 14.dp, tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Contact Support"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    Button(
                        onClick = {
                            activeContactActionName = "Report Issue"
                            showContactForm = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, sizeX = 14.dp, tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("Report Issue"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        activeContactActionName = "Suggest Feature"
                        showContactForm = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = null, sizeX = 14.dp, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("Suggest Exam Feature"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // --- INTERACTIVE CYBER-SECURITY & PRIVACY TRUST HUB ---
        SecurityCenterComponent(viewModel = viewModel)

        Spacer(modifier = Modifier.height(30.dp))

        // Logout action button
        OutlinedButton(
            onClick = { viewModel.performLogout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(t("Disconnect Candidate (Sign Out)"), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Contact Support sheet handler
        if (showContactForm) {
            ContactFormSheet(
                actionType = activeContactActionName,
                onDismiss = { showContactForm = false }
            )
        }

        // Edit Profile Dialog state handler
        if (showEditProfileDialog) {
            EditProfileDialog(
                viewModel = viewModel,
                onDismiss = { showEditProfileDialog = false }
            )
        }
    }
}

// Visual Circle helper
@Composable
fun strokeCircle() = RoundedCornerShape(50)

// ==========================================
// EDIT PROFILE DIALOG
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    viewModel: CareerPlusViewModel,
    onDismiss: () -> Unit
) {
    val profile = viewModel.currentUserProfile ?: return
    val context = LocalContext.current

    val statesList = listOf("Rajasthan", "Uttar Pradesh", "Central / National Domain", "Delhi NCR", "Bihar", "Madhya Pradesh", "Maharashtra", "Karnataka")
    val qualificationsList = listOf("10th Pass", "12th Pass", "Graduation", "Post Graduation", "Other")
    val categoriesList = listOf("General", "OBC (Non-Creamy Ledger)", "SC (Scheduled Caste)", "ST (Scheduled Tribe)", "EWS (Economically Weaker Section)")
    val languagesList = listOf("English", "Hindi", "Marathi", "Gujarati", "Bengali", "Tamil", "Telugu", "Kannada", "Punjabi", "Malayalam")

    // Local states mirroring previous selections or defaults
    var editFullName by remember { mutableStateOf(profile.fullName) }
    var editMobileNo by remember { mutableStateOf(profile.mobileNo) }
    var editAge by remember { mutableStateOf(profile.age) }
    var editState by remember { mutableStateOf(profile.state) }
    var editQualification by remember { mutableStateOf(profile.qualification) }
    var editCategory by remember { mutableStateOf(profile.category) }
    var editLanguage by remember { mutableStateOf(profile.preferredLanguage) }

    var expandedState by remember { mutableStateOf(false) }
    var expandedQual by remember { mutableStateOf(false) }
    var expandedCat by remember { mutableStateOf(false) }
    var expandedLangByDialog by remember { mutableStateOf(false) }

    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        val d = trg
        AppLocalizer.translate(text, viewModel.languageInput)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(t("Edit Profile Preferences"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Dialog")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = RoyalNavy,
                            titleContentColor = GoldenSun,
                            navigationIconContentColor = GoldenSun
                        )
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = t("Candidate ID / Contact:") + " " + profile.contact,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Name Input
                    OutlinedTextField(
                        value = editFullName,
                        onValueChange = { editFullName = it },
                        label = { Text(t("Full Name (as in Matriculation)")) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_fullName_input")
                    )

                    // Mobile No Input
                    OutlinedTextField(
                        value = editMobileNo,
                        onValueChange = { editMobileNo = it },
                        label = { Text(t("Mobile Contact Number (10 Digit)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("edit_mobile_input")
                    )

                    // Age Input
                    OutlinedTextField(
                        value = editAge,
                        onValueChange = { editAge = it },
                        label = { Text(t("Candidate Age (Years)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("edit_age_input")
                    )

                    // State Dropdown selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedState,
                            onExpandedChange = { expandedState = !expandedState }
                        ) {
                            OutlinedTextField(
                                value = editState,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(t("Preferred State / Alert Sector")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedState,
                                onDismissRequest = { expandedState = false }
                            ) {
                                statesList.forEach { stateText ->
                                    DropdownMenuItem(
                                        text = { Text(stateText) },
                                        onClick = {
                                            editState = stateText
                                            expandedState = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Qualification Dropdown selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedQual,
                            onExpandedChange = { expandedQual = !expandedQual }
                        ) {
                            OutlinedTextField(
                                value = editQualification,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(t("Highest Educational Qualification")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedQual) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedQual,
                                onDismissRequest = { expandedQual = false }
                            ) {
                                qualificationsList.forEach { qual ->
                                    DropdownMenuItem(
                                        text = { Text(qual) },
                                        onClick = {
                                            editQualification = qual
                                            expandedQual = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Category Dropdown selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedCat,
                            onExpandedChange = { expandedCat = !expandedCat }
                        ) {
                            OutlinedTextField(
                                value = editCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(t("Reservation Category Class")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCat) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCat,
                                onDismissRequest = { expandedCat = false }
                            ) {
                                categoriesList.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            editCategory = cat
                                            expandedCat = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Preferred Language Dropdown selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedLangByDialog,
                            onExpandedChange = { expandedLangByDialog = !expandedLangByDialog }
                        ) {
                            OutlinedTextField(
                                value = editLanguage,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(t("System Preferred Translation Language")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLangByDialog) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedLangByDialog,
                                onDismissRequest = { expandedLangByDialog = false }
                            ) {
                                languagesList.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang) },
                                        onClick = {
                                            editLanguage = lang
                                            expandedLangByDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (editFullName.isBlank()) {
                                Toast.makeText(context, t("Full name is mandatory."), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (editMobileNo.length < 10) {
                                Toast.makeText(context, t("Enter a valid 10-digit mobile phone.") , Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Sync profile changes under saved ID
                            viewModel.updateUserProfile(
                                fullName = editFullName,
                                mobileNo = editMobileNo,
                                state = editState,
                                qualification = editQualification,
                                age = editAge,
                                category = editCategory,
                                preferredLanguage = editLanguage
                            )

                            Toast.makeText(context, t("Profile adjustments saved and synced!"), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_profile_edits_button")
                    ) {
                        Text(t("Save & Synchronize"), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

// ==========================================
// NOTIFICATIONS DIALOG / SLIDE PANEL
// ==========================================
@Composable
fun NotificationPanelSheet(
    viewModel: CareerPlusViewModel,
    onDismiss: () -> Unit
) {
    val activeNotifications by viewModel.allNotifications.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Notifications, contentDescription = null, sizeX = 24.dp, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scheduler & Reminders", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.EmptyClose, contentDescription = "Close sheet", tint = Color.White)
                    }
                }

                if (activeNotifications.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Zero alerts active currently.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeNotifications) { alert ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (alert.type) {
                                            "deadline" -> Icons.Default.WarningAmber
                                            "update" -> Icons.Default.DateRange
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = when (alert.type) {
                                            "deadline" -> Color.Red
                                            "update" -> GoldenSun
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        sizeX = 24.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(alert.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(alert.content, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                // Bottom disclaimer
                Text(
                    text = "Smart reminders automatically adjust based on saved vacancies last date countdowns (7, 5, 3 and 1 days tags).",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

val Icons.EmptyClose get() = Icons.Default.Close

// ==========================================
// FEEDBACK / CONTACT SUPPORT SHEETS
// ==========================================
@Composable
fun ContactFormSheet(
    actionType: String,
    onDismiss: () -> Unit
) {
    var subjectInput by remember { mutableStateOf("") }
    var descriptionInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = "Helpline Form: $actionType", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = { subjectInput = it },
                    label = { Text("Direct Subject Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Details describing your $actionType request") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (subjectInput.isBlank() || descriptionInput.isBlank()) {
                                Toast.makeText(context, "All parameters must be completed to dispatch mail.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("roylabs0@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "[$actionType] $subjectInput")
                                putExtra(Intent.EXTRA_TEXT, descriptionInput)
                            }
                            try {
                                context.startActivity(Intent.createChooser(mailIntent, "Submit Helpline Request"))
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Gmail Client missing. Directing to web browser.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Send Mail", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMOB BANNER COMPOSABLE (SDK + Simulation)
// ==========================================
@Composable
fun AdMobBanner(
    adUnitId: String,
    isPro: Boolean,
    screenContext: String
) {
    if (isPro) return

    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    setAdUnitId(adUnitId)
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            hasError = true
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            },
            update = { adView ->
                try {
                    adView.loadAd(AdRequest.Builder().build())
                } catch(e: Exception) {}
            }
        )

        // Robust simulated premium fallback matching screenContext
        if (hasError || true) { // Render fallback in emulator to preserve beautiful feedback
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .background(GoldenSun, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Ad", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val adText = when (screenContext) {
                            "HOME" -> Pair("Live DSSSB Teacher classes", "Join live batch starting tomorrow! Exam series included.")
                            "JOBS" -> Pair("Official UPSC Prelims Test series", "15,000 students registered already. ₹199 only")
                            "RESULTS" -> Pair("Join CareerPlus Pro Today @ ₹49", "Remove all matching banners and native advertisements!")
                            else -> Pair("Banking Reasoning Speed Series", "Solved mock questions with visual maps & guides.")
                        }
                        Text(adText.first, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(adText.second, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Button(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("Apply", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// ADMOB NATIVE COMPOSABLE (Simulation)
// ==========================================
@Composable
fun AdMobNativeListingAd(
    adUnitId: String,
    isPro: Boolean
) {
    if (isPro) return
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, GoldenSun.copy(0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(GoldenSun, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Ad • Sponsored Sponsor", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("National Test Prep Academy", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Text(
                    text = "ID: ca-app-pub-native",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Full Syllabus UPSC IAS & RPSC Programmer Mock Series 2026",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Secure elite counseling, download verified PDFs, and get daily study planners custom integrated. Offer scales out tonight.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
                    context.startActivity(browserIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenSun, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Reserve Free Trial Call", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

// ==========================================
// SIMULATED INTERSTITIAL DIALOG AD
// ==========================================
@Composable
fun SimulatedInterstitialDialog(
    actionTag: String,
    onDismiss: () -> Unit
) {
    var dismissalSecs by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (dismissalSecs > 0) {
            delay(1000)
            dismissalSecs--
        }
    }

    Dialog(
        onDismissRequest = {
            if (dismissalSecs <= 0) onDismiss()
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.LightGray, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        Text("Ad • Interstitial Unit Identifier", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    TextButton(
                        onClick = { if (dismissalSecs <= 0) onDismiss() },
                        enabled = dismissalSecs <= 0
                    ) {
                        Text(
                            text = if (dismissalSecs > 0) "Close in ${dismissalSecs}s" else "[X] Close",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (dismissalSecs > 0) Color.Gray else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldenSun, modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "CareerPlus Smart Coaching",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Formula test templates, custom mock alerts tracker and interactive date tables. Supercharge your path completely ad-free.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock CareerPlus Coach Unbound", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// SIMULATED REWARDED VIDEO AD DIALOG
// ==========================================
@Composable
fun SimulatedRewardedAdDialog(
    onAdCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var timeLeft by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        onAdCompleted()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(GoldenSun, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        Text("Partner Rewarded Ad", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    
                    Text("Granting reward in: $timeLeft", color = Color.White, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(48.dp))

                CircularProgressIndicator(color = GoldenSun)

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "SBI PO Test-Series Advertisement Sponsor",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Do not minimize or close. Your free CareerCoach consultation credits is validating directly...",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun OptInComponentVisuals(content: @Composable () -> Unit) {
    content()
}
