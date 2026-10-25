package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.HuaweiHealthBridgeManager
import com.example.data.AppDatabase
import com.example.data.HealthRepository
import com.example.ui.HealthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HealthSync", appName)
    }

    @Test
    fun `repository initializes structural categories without fake metrics or fake logs`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = HealthRepository(db.healthDao())

        repository.initializeStructuralDefaultsIfEmpty()

        val categories = repository.connections.first()
        val todayMetric = repository.todayMetric.first()
        val logs = repository.syncLogs.first()
        val settings = repository.userSettings.first()

        assertEquals(6, categories.size)
        assertTrue(categories.filter { it.isConnected }.all { it.lastSyncTime == "Never synced" })
        assertNull("Today metric must be null until real Health Connect or Huawei Cloud data is read", todayMetric)
        assertTrue("Sync logs must be empty on fresh install", logs.isEmpty())
        assertEquals("Never synced", settings?.lastSyncTimestampText)

        val viewModel = HealthViewModel(repository)
        assertFalse("Huawei account must not be pre-linked with fake credentials", viewModel.huaweiAccountState.value.isLinked)

        db.close()
    }

    @Test
    fun `parses user provided Huawei CAS URL and intercepts redirect callback`() {
        val parsed = HuaweiHealthBridgeManager.parseHuaweiUrl(HuaweiHealthBridgeManager.USER_PROVIDED_CAS_URL)
        assertEquals("103198029", parsed.clientId)
        assertEquals("https://server.healthsync.app/huawei", parsed.redirectUri)
        assertEquals(16, parsed.scopes.size)
        assertTrue(parsed.scopes.contains("https://www.huawei.com/healthkit/step.read"))
        assertTrue(parsed.scopes.contains("https://www.huawei.com/healthkit/heartrate.read"))

        val pkceUrl = HuaweiHealthBridgeManager.buildFreshPkceHuaweiOAuthUrl()
        assertTrue(pkceUrl.contains("client_id=103198029"))
        assertTrue(pkceUrl.contains("code_challenge_method=S256"))

        val redirectCallback = "https://server.healthsync.app/huawei?code=AUTH_CODE_XYZ&state=healthsync_pkce_auth"
        assertTrue(HuaweiHealthBridgeManager.isOAuthRedirectCallback(redirectCallback))
        val parsedCallback = HuaweiHealthBridgeManager.parseHuaweiUrl(redirectCallback)
        assertEquals("AUTH_CODE_XYZ", parsedCallback.extractedAuthCode)
    }
}
