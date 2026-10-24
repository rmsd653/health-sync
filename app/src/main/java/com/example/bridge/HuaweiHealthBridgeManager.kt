package com.example.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import java.net.URLEncoder
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
    val authorizedScopes: List<String> = listOf(
        "https://www.huawei.com/healthkit/step.read",
        "https://www.huawei.com/healthkit/heartrate.read",
        "https://www.huawei.com/healthkit/sleep.read",
        "https://www.huawei.com/healthkit/calories.read"
    ),
    val accessToken: String? = null,
    val lastTokenRefresh: String = "Not connected",
    val deviceModel: String = ""
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

    private const val HUAWEI_OAUTH_AUTHORIZE_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/authorize"
    private const val HUAWEI_OAUTH_TOKEN_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
    private const val HUAWEI_HEALTHKIT_POLYMERIZE_URL =
        "https://health-api.cloud.huawei.com/healthkit/v1/sampleSet:polymerize"
    const val REDIRECT_URI = "healthsync://huawei-oauth-callback"

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

    fun isHuaweiClientIdConfigured(): Boolean {
        val id = BuildConfig.HUAWEI_CLIENT_ID
        return id.isNotBlank() && id != "MY_HUAWEI_CLIENT_ID"
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

    /**
     * Builds the official Huawei ID OAuth 2.0 Authorization URL for Huawei Health Kit scopes.
     */
    fun buildHuaweiOAuthUrl(customClientId: String? = null): String {
        val configuredClientId = customClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_ID.takeIf { it.isNotBlank() && it != "MY_HUAWEI_CLIENT_ID" }
            ?: ""

        val scopes = listOf(
            "openid",
            "profile",
            "https://www.huawei.com/healthkit/step.read",
            "https://www.huawei.com/healthkit/heartrate.read",
            "https://www.huawei.com/healthkit/sleep.read",
            "https://www.huawei.com/healthkit/calories.read"
        ).joinToString(" ")

        val encodedRedirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        val encodedScopes = URLEncoder.encode(scopes, "UTF-8")

        return "$HUAWEI_OAUTH_AUTHORIZE_URL?" +
            "response_type=code" +
            "&client_id=$configuredClientId" +
            "&redirect_uri=$encodedRedirect" +
            "&scope=$encodedScopes" +
            "&access_type=offline" +
            "&state=healthsync_bridge_auth"
    }

    fun openHuaweiOAuthInBrowser(context: Context, customClientId: String? = null): Boolean {
        val clientId = customClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_ID.takeIf { it.isNotBlank() && it != "MY_HUAWEI_CLIENT_ID" }
        if (clientId.isNullOrBlank()) {
            return false
        }
        return try {
            val url = buildHuaweiOAuthUrl(clientId)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exchanges a Huawei OAuth 2.0 authorization code for a real Bearer access token
     * at https://oauth-login.cloud.huawei.com/oauth2/v3/token.
     */
    suspend fun exchangeAuthorizationCodeForToken(
        authorizationCode: String,
        customClientId: String? = null,
        customClientSecret: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val clientId = customClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_ID.takeIf { it.isNotBlank() && it != "MY_HUAWEI_CLIENT_ID" }
            ?: return@withContext Result.failure(
                IllegalStateException("HUAWEI_CLIENT_ID is not configured in Secrets")
            )

        val clientSecret = customClientSecret?.takeIf { it.isNotBlank() }
            ?: BuildConfig.HUAWEI_CLIENT_SECRET.takeIf { it.isNotBlank() && it != "MY_HUAWEI_CLIENT_SECRET" }
            ?: return@withContext Result.failure(
                IllegalStateException("HUAWEI_CLIENT_SECRET is not configured in Secrets")
            )

        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authorizationCode)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url(HUAWEI_OAUTH_TOKEN_URL)
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyStr = response.body?.string()
            if (!response.isSuccessful || bodyStr == null) {
                return@withContext Result.failure(
                    Exception("Huawei OAuth token endpoint returned HTTP ${response.code}")
                )
            }

            val json = JSONObject(bodyStr)
            val accessToken = json.optString("access_token", "")
            if (accessToken.isNotBlank()) {
                Result.success(accessToken)
            } else {
                val errorDesc = json.optString("error_description", "No access_token in Huawei response")
                Result.failure(Exception(errorDesc))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
