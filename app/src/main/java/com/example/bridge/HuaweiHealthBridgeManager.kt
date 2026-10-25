package com.example.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

data class BridgeEnvironmentStatus(
    val isHuaweiHealthInstalled: Boolean = false,
    val isHmsCoreInstalled: Boolean = false,
    val healthConnectSdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val isBatteryOptimizationIgnored: Boolean = false,
    val grantedPermissionsCount: Int = 0,
    val totalPermissionsCount: Int = 8,
    val grantedPermissions: Set<String> = emptySet()
) {
    val isHealthConnectAvailable: Boolean
        get() = healthConnectSdkStatus == HealthConnectClient.SDK_AVAILABLE

    val allHealthConnectPermissionsGranted: Boolean
        get() = grantedPermissionsCount >= totalPermissionsCount && totalPermissionsCount > 0

    val hasAnyReadPermission: Boolean
        get() = grantedPermissions.any { it.contains("READ_") }
}

data class HuaweiAccountState(
    val isLinked: Boolean = false,
    val accountIdentifier: String = "",
    val authMode: String = "Not Connected",
    val clientId: String = HuaweiHealthBridgeManager.DEFAULT_HEALTHSYNC_CLIENT_ID,
    val redirectUri: String = HuaweiHealthBridgeManager.DEFAULT_HEALTHSYNC_REDIRECT_URI,
    val authorizedScopes: List<String> = HuaweiHealthBridgeManager.FULL_HEALTHSYNC_SCOPES,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val lastAuthCode: String? = null,
    val lastTokenRefresh: String = "Not connected",
    val deviceModel: String = ""
)

data class ParsedHuaweiUrlInfo(
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
    val extractedAuthCode: String? = null,
    val extractedAccessToken: String? = null,
    val regionCode: String = "ae"
)

data class HuaweiTokenExchangeResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long,
    val scope: String
)

data class BridgeSyncStepResult(
    val stepNumber: Int,
    val title: String,
    val detail: String,
    val status: String // "PENDING", "RUNNING", "SUCCESS", "WARN", "ERROR"
)

data class HuaweiCloudDailyMetrics(
    val steps: Int,
    val heartRateBpm: Int,
    val sleepMinutes: Int,
    val activeCaloriesKcal: Int
)

data class HealthConnectReadbackResult(
    val hasAnyData: Boolean,
    val steps: Int,
    val heartRateBpm: Int,
    val sleepMinutes: Int,
    val activeCaloriesKcal: Int,
    val statusMessage: String
)

data class HealthConnectVerificationSummary(
    val writtenToHealthConnect: Boolean,
    val recordsWrittenCount: Int,
    val verifiedSteps: Long,
    val verifiedHeartRateAvg: Long,
    val verifiedSleepMinutes: Long,
    val verifiedActiveCaloriesKcal: Int,
    val statusMessage: String
)

object HuaweiHealthBridgeManager {

    const val HUAWEI_HEALTH_PACKAGE = "com.huawei.health"
    const val HMS_CORE_PACKAGE = "com.huawei.hwid"
    const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

    // Extracted directly from the user's Health Sync Huawei OAuth URL
    const val DEFAULT_HEALTHSYNC_CLIENT_ID = "103198029"
    const val DEFAULT_HEALTHSYNC_REDIRECT_URI = "https://server.healthsync.app/huawei"
    const val CUSTOM_APP_REDIRECT_URI = "healthsync://huawei-oauth-callback"

    const val USER_PROVIDED_CAS_URL =
        "https://id5.cloud.huawei.com/CAS/mobile/standard/welcome.html?reqClientType=90&loginChannel=90000300&regionCode=ae&loginUrl=https%3A%2F%2Fid5.cloud.huawei.com%3A443%2FCAS%2Fmobile%2Fstandard%2Fwelcome.html&lang=en-gb&themeName=huawei&clientID=103198029&service=https%3A%2F%2Foauth-login5.cloud.huawei.com%2Foauth2%2Fv3%2FloginCallback%3Faccess_type%3Doffline%26client_id%3D103198029%26code_challenge_method%3DS256%26display%3Dtouch%26flowID%3D0aa54b38000001d17928459062732772%26h%3D1792845906.3210%26include_granted_scopes%3Dtrue%26lang%3Den-gb%26nonce%3Ddefault%26prompt%3Dlogin%26redirect_uri%3Dhttps%253A%252F%252Fserver.healthsync.app%252Fhuawei%26response_type%3Dcode%26scope%3Dopenid%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fhistorydata.open%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fbodytemperature.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Foxygensaturation.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fbloodglucose.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fbloodpressure.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fheightweight.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fheartrate.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fsleep.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Factivity.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Factivityrecord.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fcalories.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fdistance.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fspeed.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Flocation.read%2Bhttps%253A%252F%252Fwww.huawei.com%252Fhealthkit%252Fstep.read%26state%3DfH0tKqeY3-pvYj1asGrfd9%253AAPA91bFQkE1rT0oXf2MQ1TRMuIhHxPRf_sSXEbdLdGzPmf4Am8MtdxVxFsbwco76c500z8ulq5UCuqmW_KPxQ1JqU1qY8w5kHi8xBPoFK4SiZ4PIDiJnjrY%26v%3D700afb79f635856afa1eb528320f137261269f51c704c606fcc72a700369c125&validated=true"

    val FULL_HEALTHSYNC_SCOPES: List<String> = listOf(
        "openid",
        "https://www.huawei.com/healthkit/historydata.open",
        "https://www.huawei.com/healthkit/bodytemperature.read",
        "https://www.huawei.com/healthkit/oxygensaturation.read",
        "https://www.huawei.com/healthkit/bloodglucose.read",
        "https://www.huawei.com/healthkit/bloodpressure.read",
        "https://www.huawei.com/healthkit/heightweight.read",
        "https://www.huawei.com/healthkit/heartrate.read",
        "https://www.huawei.com/healthkit/sleep.read",
        "https://www.huawei.com/healthkit/activity.read",
        "https://www.huawei.com/healthkit/activityrecord.read",
        "https://www.huawei.com/healthkit/calories.read",
        "https://www.huawei.com/healthkit/distance.read",
        "https://www.huawei.com/healthkit/speed.read",
        "https://www.huawei.com/healthkit/location.read",
        "https://www.huawei.com/healthkit/step.read"
    )

    private const val HUAWEI_OAUTH_AUTHORIZE_URL_REGIONAL = "https://oauth-login5.cloud.huawei.com/oauth2/v3/authorize"
    private const val HUAWEI_OAUTH_TOKEN_URL_REGIONAL = "https://oauth-login5.cloud.huawei.com/oauth2/v3/token"
    private const val HUAWEI_OAUTH_TOKEN_URL_GLOBAL = "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
    private const val HUAWEI_HEALTHKIT_POLYMERIZE_URL =
        "https://health-api.cloud.huawei.com/healthkit/v1/sampleSet:polymerize"

    // Active PKCE session state for automatic authorization-code-to-token exchange
    @Volatile
    var activePkceVerifier: String = generateCodeVerifier()
        private set

    @Volatile
    var activeClientId: String = DEFAULT_HEALTHSYNC_CLIENT_ID
        private set

    @Volatile
    var activeRedirectUri: String = DEFAULT_HEALTHSYNC_REDIRECT_URI
        private set

    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun computeCodeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Builds a fresh PKCE S256 OAuth 2.0 authorization URL matching the exact Huawei Health Sync parameters
     * (client_id=103198029, redirect_uri=https://server.healthsync.app/huawei, all 16 HealthKit scopes)
     * while generating a fresh PKCE code_verifier owned by this app session.
     */
    fun buildFreshPkceHuaweiOAuthUrl(
        clientId: String = DEFAULT_HEALTHSYNC_CLIENT_ID,
        redirectUri: String = DEFAULT_HEALTHSYNC_REDIRECT_URI,
        responseType: String = "code"
    ): String {
        val resolvedClientId = clientId.trim().ifBlank { DEFAULT_HEALTHSYNC_CLIENT_ID }
        val resolvedRedirectUri = redirectUri.trim().ifBlank { DEFAULT_HEALTHSYNC_REDIRECT_URI }

        val verifier = generateCodeVerifier()
        val challenge = computeCodeChallengeS256(verifier)

        activePkceVerifier = verifier
        activeClientId = resolvedClientId
        activeRedirectUri = resolvedRedirectUri

        val scopesString = FULL_HEALTHSYNC_SCOPES.joinToString(" ")
        val encodedRedirect = URLEncoder.encode(resolvedRedirectUri, "UTF-8")
        val encodedScopes = URLEncoder.encode(scopesString, "UTF-8")

        return "$HUAWEI_OAUTH_AUTHORIZE_URL_REGIONAL?" +
            "access_type=offline" +
            "&client_id=$resolvedClientId" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&display=touch" +
            "&include_granted_scopes=true" +
            "&lang=en-gb" +
            "&nonce=default" +
            "&prompt=login" +
            "&redirect_uri=$encodedRedirect" +
            "&response_type=${URLEncoder.encode(responseType, "UTF-8")}" +
            "&scope=$encodedScopes" +
            "&state=healthsync_pkce_auth"
    }

    /**
     * Parses any Huawei CAS welcome URL, OAuth authorize URL, or redirect callback URL
     * to extract client_id, redirect_uri, scopes, authorization code, or access_token.
     */
    fun parseHuaweiUrl(rawUrl: String): ParsedHuaweiUrlInfo {
        val trimmed = rawUrl.trim()
        return try {
            val uri = Uri.parse(trimmed)
            var clientId = uri.getQueryParameter("clientID")
                ?: uri.getQueryParameter("client_id")
                ?: DEFAULT_HEALTHSYNC_CLIENT_ID
            var redirectUri = uri.getQueryParameter("redirect_uri") ?: DEFAULT_HEALTHSYNC_REDIRECT_URI
            val regionCode = uri.getQueryParameter("regionCode") ?: "ae"
            var code = uri.getQueryParameter("code")
            var accessToken = uri.getQueryParameter("access_token")

            // Check URL fragment (#access_token=...&code=...)
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) {
                val fragUri = Uri.parse("https://dummy.local/?$fragment")
                if (accessToken.isNullOrBlank()) {
                    accessToken = fragUri.getQueryParameter("access_token")
                }
                if (code.isNullOrBlank()) {
                    code = fragUri.getQueryParameter("code")
                }
            }

            var scopes = FULL_HEALTHSYNC_SCOPES

            // If it's a CAS welcome.html URL with a nested `service=` URL, decode and inspect `service`
            val serviceParam = uri.getQueryParameter("service")
            if (!serviceParam.isNullOrBlank()) {
                val decodedService = try {
                    URLDecoder.decode(serviceParam, "UTF-8")
                } catch (_: Exception) {
                    serviceParam
                }
                val serviceUri = Uri.parse(decodedService)
                serviceUri.getQueryParameter("client_id")?.let { clientId = it }
                serviceUri.getQueryParameter("redirect_uri")?.let { redirectUri = it }
                serviceUri.getQueryParameter("scope")?.let { scopeStr ->
                    val parsedScopes = scopeStr.split(" ", "+").filter { it.isNotBlank() }
                    if (parsedScopes.isNotEmpty()) {
                        scopes = parsedScopes
                    }
                }
                if (code.isNullOrBlank()) {
                    code = serviceUri.getQueryParameter("code")
                }
            }

            ParsedHuaweiUrlInfo(
                clientId = clientId,
                redirectUri = redirectUri,
                scopes = scopes,
                extractedAuthCode = code,
                extractedAccessToken = accessToken,
                regionCode = regionCode
            )
        } catch (_: Exception) {
            ParsedHuaweiUrlInfo(
                clientId = DEFAULT_HEALTHSYNC_CLIENT_ID,
                redirectUri = DEFAULT_HEALTHSYNC_REDIRECT_URI,
                scopes = FULL_HEALTHSYNC_SCOPES
            )
        }
    }

    /**
     * Checks if a URL navigated to inside the in-app WebView is the OAuth redirect callback
     * containing `code=` or `access_token=`.
     */
    fun isOAuthRedirectCallback(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("https://server.healthsync.app/huawei") ||
            lower.startsWith("healthsync://huawei-oauth-callback") ||
            lower.startsWith("hms://redirect_url") ||
            (lower.contains("code=") && !lower.contains("welcome.html") && !lower.contains("logincallback")) ||
            lower.contains("access_token=")
    }

    /**
     * Exchanges a Huawei OAuth 2.0 authorization code for a real Bearer access_token
     * using PKCE (`code_verifier`) and optional `client_secret` across Huawei's regional and global token endpoints.
     */
    suspend fun exchangeAuthorizationCodeForToken(
        authorizationCode: String,
        customClientId: String? = null,
        customRedirectUri: String? = null,
        codeVerifier: String? = activePkceVerifier,
        customClientSecret: String? = null
    ): Result<HuaweiTokenExchangeResponse> = withContext(Dispatchers.IO) {
        val clientId = customClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_ID.takeIf { it.isNotBlank() && it != "MY_HUAWEI_CLIENT_ID" }
            ?: activeClientId.ifBlank { DEFAULT_HEALTHSYNC_CLIENT_ID }

        val redirectUri = customRedirectUri?.takeIf { it.isNotBlank() }
            ?: activeRedirectUri.ifBlank { DEFAULT_HEALTHSYNC_REDIRECT_URI }

        val clientSecret = customClientSecret?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_SECRET.takeIf { it.isNotBlank() && it != "MY_HUAWEI_CLIENT_SECRET" }

        val endpoints = listOf(HUAWEI_OAUTH_TOKEN_URL_REGIONAL, HUAWEI_OAUTH_TOKEN_URL_GLOBAL)
        var lastError = "Unable to exchange authorization code"

        for (endpoint in endpoints) {
            try {
                val formBuilder = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", authorizationCode.trim())
                    .add("client_id", clientId)
                    .add("redirect_uri", redirectUri)

                if (!codeVerifier.isNullOrBlank()) {
                    formBuilder.add("code_verifier", codeVerifier)
                }
                if (!clientSecret.isNullOrBlank()) {
                    formBuilder.add("client_secret", clientSecret)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .post(formBuilder.build())
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""

                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    val json = JSONObject(bodyStr)
                    val accessToken = json.optString("access_token", "")
                    if (accessToken.isNotBlank()) {
                        return@withContext Result.success(
                            HuaweiTokenExchangeResponse(
                                accessToken = accessToken,
                                refreshToken = json.optString("refresh_token", "").takeIf { it.isNotBlank() },
                                expiresInSec = json.optLong("expires_in", 3600L),
                                scope = json.optString("scope", "")
                            )
                        )
                    }
                }

                if (bodyStr.isNotBlank()) {
                    val errJson = try {
                        JSONObject(bodyStr)
                    } catch (_: Exception) {
                        null
                    }
                    val errDesc = errJson?.optString("error_description")
                        ?.takeIf { it.isNotBlank() }
                        ?: errJson?.optString("sub_error")
                        ?: "HTTP ${response.code}"
                    lastError = "Huawei OAuth ($endpoint): $errDesc"
                } else {
                    lastError = "Huawei OAuth token endpoint returned HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network error contacting Huawei OAuth"
            }
        }

        Result.failure(Exception(lastError))
    }

    suspend fun inspectEnvironment(context: Context): BridgeEnvironmentStatus = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val huaweiInstalled = isPackageInstalled(pm, HUAWEI_HEALTH_PACKAGE)
        val hmsInstalled = isPackageInstalled(pm, HMS_CORE_PACKAGE)
        val hcStatus = try {
            HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)
        } catch (_: Exception) {
            HealthConnectClient.SDK_UNAVAILABLE
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBatteryOpt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        var grantedSet = emptySet<String>()
        if (hcStatus == HealthConnectClient.SDK_AVAILABLE) {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                grantedSet = client.permissionController.getGrantedPermissions()
            } catch (_: Exception) {
            }
        }

        val matchingGranted = REQUIRED_PERMISSIONS.intersect(grantedSet)

        BridgeEnvironmentStatus(
            isHuaweiHealthInstalled = huaweiInstalled,
            isHmsCoreInstalled = hmsInstalled,
            healthConnectSdkStatus = hcStatus,
            isBatteryOptimizationIgnored = ignoringBatteryOpt,
            grantedPermissionsCount = matchingGranted.size,
            totalPermissionsCount = REQUIRED_PERMISSIONS.size,
            grantedPermissions = matchingGranted
        )
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun openHuaweiHealthApp(context: Context): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(HUAWEI_HEALTH_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://appgallery.huawei.com/Featured")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openHealthConnectSettings(context: Context): Boolean {
        return try {
            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                "android.health.connect.action.HEALTH_CONNECT_SETTINGS"
            } else {
                HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val playStoreIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playStoreIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun requestBatteryOptimizationExemption(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reads real today health metrics directly from Android's Google Health Connect OS datastore.
     * Never returns fake or random values.
     */
    suspend fun readRealMetricsFromHealthConnect(context: Context): HealthConnectReadbackResult =
        withContext(Dispatchers.IO) {
            val status = try {
                HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)
            } catch (_: Exception) {
                HealthConnectClient.SDK_UNAVAILABLE
            }

            if (status != HealthConnectClient.SDK_AVAILABLE) {
                return@withContext HealthConnectReadbackResult(
                    hasAnyData = false,
                    steps = 0,
                    heartRateBpm = 0,
                    sleepMinutes = 0,
                    activeCaloriesKcal = 0,
                    statusMessage = "Google Health Connect SDK is not available on this device."
                )
            }

            try {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()

                val canReadSteps = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
                val canReadHr = granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class))
                val canReadSleep = granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
                val canReadCalories = granted.contains(
                    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
                )

                if (!canReadSteps && !canReadHr && !canReadSleep && !canReadCalories) {
                    return@withContext HealthConnectReadbackResult(
                        hasAnyData = false,
                        steps = 0,
                        heartRateBpm = 0,
                        sleepMinutes = 0,
                        activeCaloriesKcal = 0,
                        statusMessage = "Health Connect read permissions have not been granted yet."
                    )
                }

                val zoneId = ZoneId.systemDefault()
                val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
                val sleepWindowStart = startOfToday.minus(12, ChronoUnit.HOURS)
                val now = Instant.now().plus(1, ChronoUnit.MINUTES)

                val requestedMetrics = mutableSetOf<androidx.health.connect.client.aggregate.AggregateMetric<*>>()
                if (canReadSteps) requestedMetrics.add(StepsRecord.COUNT_TOTAL)
                if (canReadHr) requestedMetrics.add(HeartRateRecord.BPM_AVG)
                if (canReadCalories) requestedMetrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)

                var aggSteps = 0
                var aggHr = 0
                var aggCal = 0

                if (requestedMetrics.isNotEmpty()) {
                    val todayAggregate = client.aggregate(
                        AggregateRequest(
                            metrics = requestedMetrics,
                            timeRangeFilter = TimeRangeFilter.between(startOfToday, now)
                        )
                    )
                    aggSteps = (todayAggregate[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
                    aggHr = (todayAggregate[HeartRateRecord.BPM_AVG] ?: 0L).toInt()
                    aggCal = todayAggregate[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                        ?.inKilocalories?.toInt() ?: 0
                }

                var aggSleepMins = 0
                if (canReadSleep) {
                    val sleepAggregate = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, now)
                        )
                    )
                    aggSleepMins = (sleepAggregate[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L).toInt()
                }

                val hasAny = aggSteps > 0 || aggHr > 0 || aggSleepMins > 0 || aggCal > 0
                HealthConnectReadbackResult(
                    hasAnyData = hasAny,
                    steps = aggSteps,
                    heartRateBpm = aggHr,
                    sleepMinutes = aggSleepMins,
                    activeCaloriesKcal = aggCal,
                    statusMessage = if (hasAny) {
                        "Read live today totals from Google Health Connect."
                    } else {
                        "Connected to Health Connect (0 records recorded for today yet)."
                    }
                )
            } catch (e: Exception) {
                HealthConnectReadbackResult(
                    hasAnyData = false,
                    steps = 0,
                    heartRateBpm = 0,
                    sleepMinutes = 0,
                    activeCaloriesKcal = 0,
                    statusMessage = "Health Connect read error: ${e.localizedMessage ?: "Unable to read records"}"
                )
            }
        }

    /**
     * Queries Huawei Health Kit REST API (sampleSet:polymerize) for real Steps, Heart Rate, Sleep, and Calories.
     * Requires a valid Huawei OAuth 2.0 Bearer access token.
     */
    suspend fun fetchHuaweiCloudDailyMetrics(
        accessToken: String,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Result<HuaweiCloudDailyMetrics> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("polymerizeWith", JSONArray().apply {
                    put(JSONObject().put("dataTypeName", "com.huawei.continuous.steps.delta"))
                    put(JSONObject().put("dataTypeName", "com.huawei.instantaneous.heart_rate"))
                    put(JSONObject().put("dataTypeName", "com.huawei.continuous.calories.burnt"))
                    put(JSONObject().put("dataTypeName", "com.huawei.continuous.sleep.fragment"))
                })
                put("startTime", startTimeMillis)
                put("endTime", endTimeMillis)
                put("groupByTime", JSONObject().apply {
                    put("duration", (endTimeMillis - startTimeMillis).coerceAtLeast(60_000L))
                })
            }

            val request = Request.Builder()
                .url(HUAWEI_HEALTHKIT_POLYMERIZE_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyStr = response.body?.string()
            if (!response.isSuccessful || bodyStr == null) {
                return@withContext Result.failure(
                    Exception("Huawei Health Kit Cloud API returned HTTP ${response.code}")
                )
            }

            var totalSteps = 0
            var heartRateSum = 0
            var heartRateCount = 0
            var totalCaloriesKcal = 0.0
            var totalSleepMinutes = 0

            val json = JSONObject(bodyStr)
            val groups = json.optJSONArray("group")
            if (groups != null) {
                for (g in 0 until groups.length()) {
                    val sampleSets = groups.getJSONObject(g).optJSONArray("sampleSet") ?: continue
                    for (s in 0 until sampleSets.length()) {
                        val setObj = sampleSets.getJSONObject(s)
                        val dataCollectorId = setObj.optString("dataCollectorId", "")
                        val points = setObj.optJSONArray("samplePoints") ?: continue
                        for (p in 0 until points.length()) {
                            val point = points.getJSONObject(p)
                            val dataTypeName = point.optString("dataTypeName", dataCollectorId)
                            val values = point.optJSONArray("value") ?: continue
                            if (values.length() == 0) continue
                            val firstVal = values.getJSONObject(0)

                            when {
                                dataTypeName.contains("steps", ignoreCase = true) -> {
                                    totalSteps += firstVal.optInt("integerValue", 0)
                                }
                                dataTypeName.contains("heart_rate", ignoreCase = true) -> {
                                    val bpm = firstVal.optDouble("floatValue", 0.0).toInt()
                                        .takeIf { it > 0 } ?: firstVal.optInt("integerValue", 0)
                                    if (bpm > 0) {
                                        heartRateSum += bpm
                                        heartRateCount++
                                    }
                                }
                                dataTypeName.contains("calories", ignoreCase = true) -> {
                                    val cal = firstVal.optDouble("floatValue", 0.0)
                                        .takeIf { it > 0.0 } ?: firstVal.optInt("integerValue", 0).toDouble()
                                    totalCaloriesKcal += cal
                                }
                                dataTypeName.contains("sleep", ignoreCase = true) -> {
                                    val startNano = point.optLong("startTime", 0L)
                                    val endNano = point.optLong("endTime", 0L)
                                    if (endNano > startNano) {
                                        val durationMins = ((endNano - startNano) / 60_000_000_000L).toInt()
                                        totalSleepMinutes += durationMins.coerceAtLeast(0)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val avgHr = if (heartRateCount > 0) heartRateSum / heartRateCount else 0
            Result.success(
                HuaweiCloudDailyMetrics(
                    steps = totalSteps,
                    heartRateBpm = avgHr,
                    sleepMinutes = totalSleepMinutes,
                    activeCaloriesKcal = totalCaloriesKcal.toInt()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Writes only non-zero real Huawei Health records into Android's Google Health Connect datastore
     * and reads back the aggregated verification totals.
     */
    suspend fun pushHuaweiMetricsToHealthConnect(
        context: Context,
        steps: Int,
        heartRateBpm: Int,
        sleepMinutes: Int,
        activeCaloriesKcal: Int,
        huaweiDeviceModel: String = "Huawei Wearable"
    ): HealthConnectVerificationSummary = withContext(Dispatchers.IO) {
        val status = try {
            HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)
        } catch (_: Exception) {
            HealthConnectClient.SDK_UNAVAILABLE
        }

        if (status != HealthConnectClient.SDK_AVAILABLE) {
            return@withContext HealthConnectVerificationSummary(
                writtenToHealthConnect = false,
                recordsWrittenCount = 0,
                verifiedSteps = steps.toLong(),
                verifiedHeartRateAvg = heartRateBpm.toLong(),
                verifiedSleepMinutes = sleepMinutes.toLong(),
                verifiedActiveCaloriesKcal = activeCaloriesKcal,
                statusMessage = "Google Health Connect SDK is not available on this device."
            )
        }

        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()

            val canWriteSteps = granted.contains(HealthPermission.getWritePermission(StepsRecord::class))
            val canWriteHr = granted.contains(HealthPermission.getWritePermission(HeartRateRecord::class))
            val canWriteSleep = granted.contains(HealthPermission.getWritePermission(SleepSessionRecord::class))
            val canWriteCalories = granted.contains(
                HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
            )

            if (!canWriteSteps && !canWriteHr && !canWriteSleep && !canWriteCalories) {
                return@withContext HealthConnectVerificationSummary(
                    writtenToHealthConnect = false,
                    recordsWrittenCount = 0,
                    verifiedSteps = steps.toLong(),
                    verifiedHeartRateAvg = heartRateBpm.toLong(),
                    verifiedSleepMinutes = sleepMinutes.toLong(),
                    verifiedActiveCaloriesKcal = activeCaloriesKcal,
                    statusMessage = "Health Connect write permissions are required to insert Huawei records."
                )
            }

            val now = Instant.now()
            val zoneId = ZoneId.systemDefault()
            val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(now)

            val huaweiWatchDevice = Device(
                manufacturer = "HUAWEI",
                model = huaweiDeviceModel.ifBlank { "Huawei Health" },
                type = Device.TYPE_WATCH
            )
            val recordMetadata = Metadata(device = huaweiWatchDevice)

            val recordsToInsert = mutableListOf<androidx.health.connect.client.records.Record>()

            if (canWriteSteps && steps > 0) {
                recordsToInsert.add(
                    StepsRecord(
                        count = steps.toLong(),
                        startTime = startOfToday,
                        endTime = now,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        metadata = recordMetadata
                    )
                )
            }

            if (canWriteHr && heartRateBpm in 30..220) {
                recordsToInsert.add(
                    HeartRateRecord(
                        startTime = now.minus(5, ChronoUnit.MINUTES),
                        endTime = now,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        samples = listOf(
                            HeartRateRecord.Sample(
                                time = now.minus(1, ChronoUnit.MINUTES),
                                beatsPerMinute = heartRateBpm.toLong()
                            )
                        ),
                        metadata = recordMetadata
                    )
                )
            }

            if (canWriteSleep && sleepMinutes > 0) {
                val sleepEnd = startOfToday.plus(7, ChronoUnit.HOURS).coerceAtMost(now)
                val sleepStart = sleepEnd.minus(sleepMinutes.toLong(), ChronoUnit.MINUTES)
                recordsToInsert.add(
                    SleepSessionRecord(
                        startTime = sleepStart,
                        endTime = sleepEnd,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        title = "Huawei Health Sleep Sync",
                        notes = "Synced from Huawei Health Kit via HealthSync",
                        metadata = recordMetadata
                    )
                )
            }

            if (canWriteCalories && activeCaloriesKcal > 0) {
                recordsToInsert.add(
                    ActiveCaloriesBurnedRecord(
                        energy = Energy.kilocalories(activeCaloriesKcal.toDouble()),
                        startTime = startOfToday,
                        endTime = now,
                        startZoneOffset = zoneOffset,
                        endZoneOffset = zoneOffset,
                        metadata = recordMetadata
                    )
                )
            }

            if (recordsToInsert.isNotEmpty()) {
                client.insertRecords(recordsToInsert)
            }

            val readback = readRealMetricsFromHealthConnect(context)

            HealthConnectVerificationSummary(
                writtenToHealthConnect = recordsToInsert.isNotEmpty(),
                recordsWrittenCount = recordsToInsert.size,
                verifiedSteps = readback.steps.toLong(),
                verifiedHeartRateAvg = readback.heartRateBpm.toLong(),
                verifiedSleepMinutes = readback.sleepMinutes.toLong(),
                verifiedActiveCaloriesKcal = readback.activeCaloriesKcal,
                statusMessage = if (recordsToInsert.isNotEmpty()) {
                    "Wrote ${recordsToInsert.size} Huawei Health record(s) to Google Health Connect."
                } else {
                    "Verified Health Connect totals (0 new Huawei records to write)."
                }
            )
        } catch (e: Exception) {
            HealthConnectVerificationSummary(
                writtenToHealthConnect = false,
                recordsWrittenCount = 0,
                verifiedSteps = steps.toLong(),
                verifiedHeartRateAvg = heartRateBpm.toLong(),
                verifiedSleepMinutes = sleepMinutes.toLong(),
                verifiedActiveCaloriesKcal = activeCaloriesKcal,
                statusMessage = "Health Connect write failed: ${e.localizedMessage ?: "Check permissions"}"
            )
        }
    }
}
