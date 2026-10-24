# HealthSync — Google Material Design 3 Redesign & Real Health Data Architecture

## 1. Confirmed Product & Architecture Decisions

- **Real Data Source Strategy (`Huawei Cloud OAuth API + Health Connect Readback`)**:
  - **Zero Fake or Seeded Metrics**: All hardcoded metrics (`8,432` steps, `68` bpm, `7h 24m` sleep, `420` kcal), random step generators, fake pre-linked accounts (`huawei.user@cloud.huawei.com`), and seeded sync history logs are removed.
  - **Live Health Connect Readback**: On startup and whenever permissions change, the app queries Android's real `HealthConnectClient.aggregate()` API for today's actual `StepsRecord.COUNT_TOTAL`, `HeartRateRecord.BPM_AVG`, `SleepSessionRecord.SLEEP_DURATION_TOTAL`, and `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`.
  - **Real Huawei Cloud OAuth 2.0 & Polymerize Sync**: When connected via Huawei ID OAuth 2.0 (`https://oauth-login.cloud.huawei.com/oauth2/v3/authorize` + token exchange at `/oauth2/v3/token` or direct Bearer access token), the app queries Huawei's Health Kit Cloud REST endpoint (`https://health-api.cloud.huawei.com/healthkit/v1/sampleSet:polymerize`) for today's real time window, writes returned records into Android's `HealthConnectClient`, and refreshes the dashboard from real Health Connect totals.
- **Visual Design Direction (`Google Health Connect System App Style`)**:
  - **Authentic Google Material Design 3**: Uses Android 12+ Material You dynamic colors (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) with an official Google Health Connect M3 fallback palette (`#0B57D0` Google Primary Blue, `#006A60` Health Connect Teal, `#F8FAFD` Light Surface, `#EFF3F8` Surface Container, and `#1F1F1F` On-Surface).
  - **Google System Component Language**: Replaces dark/neon gradients and custom bars with standard M3 `TopAppBar`, `NavigationBar` with pill active indicators, tonal `Card` containers (`24dp` and `16dp` corners), M3 `ListItem` rows with leading tonal icon circles, and `FilterChip` selectors.
- **Pre-Connection Dashboard Behavior (`Clean Not Synced Empty State with Setup Action`)**:
  - When Huawei Health is not linked or Health Connect permissions have not yet been granted (or zero records exist for today), the dashboard displays a clean Google Health Connect empty state card explaining which step is pending and providing a primary action button to open the setup flow or request permissions.

---

## 2. Visual Direction & Screen Experience

### Color & Surface Hierarchy (Google Material Design 3)
- **Primary**: Google Blue (`#0B57D0` light / `#A8C7FA` dark) with Primary Container (`#D3E3FD` light / `#0842A0` dark).
- **Secondary**: Health Connect Teal (`#006A60` light / `#53DBC9` dark) with Secondary Container (`#74F8E5` light / `#005048` dark).
- **Tertiary**: Google Vitals Purple (`#65558F` light / `#D0BCFF` dark) for sleep & recovery indicators.
- **Surfaces**: Clean airy Google background (`#F8FAFD` light / `#111318` dark) paired with tonal surface containers (`#EFF3F8` light / `#1E2025` dark) and zero-elevation flat cards instead of heavy drop shadows.

### Key Screens
1. **Home (Health Connect Overview)**:
   - **M3 Top App Bar**: Google Health Sync title, real-time connection status chip (`Connected` / `Setup required`), and an icon action to trigger a manual sync.
   - **Setup / Not Synced Banner (When Disconnected or Unpermitted)**: Tonal `primaryContainer` / `secondaryContainer` hero card showing a 3-step checklist (*1. Health Connect SDK Installed*, *2. Health Connect Permissions Granted*, *3. Huawei ID Connected*) with a direct **Set up connection** or **Grant permissions** filled M3 button.
   - **Recent Data & Vitals Grid (When Real Data Exists)**: 2x2 tonal M3 cards displaying real today totals read from Health Connect (`Steps`, `Heart Rate`, `Sleep`, `Active Energy`) with "--" shown honestly for any metric that has zero records today.
   - **Data Categories Section**: Clean M3 `ListItem` rows with leading tonal icons, real last-synced timestamps (`Never synced` until an actual sync succeeds), and M3 `Switch` toggles.
2. **Bridge & Permissions (Huawei <-> Health Connect Setup)**:
   - Step-by-step Google Settings-style list cards for:
     - **Step 1: Huawei Health App & Cloud OAuth 2.0**: Launches Huawei OAuth browser flow (`healthsync://huawei-oauth-callback`), automatically exchanges the authorization code for an access token when `HUAWEI_CLIENT_ID` and `HUAWEI_CLIENT_SECRET` are configured, or lets the user inspect/paste a Huawei Health Kit Bearer token.
     - **Step 2: Google Health Connect Permissions**: Launches the native Android Health Connect permission dialog (`PermissionController.createRequestPermissionResultContract()`) or opens the system Health Connect settings page.
     - **Step 3: Sync Verification**: Runs a live verification pass and displays the exact HTTP/SDK status message.
3. **Activity & Sync Logs**:
   - Displays only real sync and permission events recorded during actual user sessions.
   - Shows a clean M3 empty state (*"No sync activity yet"*) with a **Sync now** button when no sync has been run.
4. **Settings & App Info**:
   - Standard Google Settings layout using grouped M3 `ListItem` components for background sync interval, Wi-Fi/charging rules, permission management shortcuts, and a **Clear local cache** action.

---

## 3. Real Data Sync & Readback Flow

```
┌──────────────────────────────┐       OAuth 2.0 / REST        ┌──────────────────────────────┐
│     Huawei Health Cloud      │◀─────────────────────────────▶│  HuaweiHealthBridgeManager   │
│  sampleSet:polymerize API    │   Steps, HR, Sleep, Calories  │   (OAuth + REST + HC SDK)    │
└──────────────────────────────┘                               └──────────────┬───────────────┘
                                                                              │
                                                        Write real records    │ Read real aggregates
                                                        insertRecords()       │ aggregate()
                                                                              ▼
┌──────────────────────────────┐     Observe StateFlow         ┌──────────────────────────────┐
│  Google M3 Compose Screens   │◀───────────────────────────── │   Android Health Connect     │
│ (Empty State or Real Totals) │       HealthViewModel         │     OS System Datastore      │
└──────────────────────────────┘                               └──────────────────────────────┘
```

### Verification Plan
1. **Zero Fake Data Verification**: Confirm that fresh installs start with `todayMetric = null`, `syncLogs = emptyList()`, `huaweiAccountState.isLinked = false`, and categories showing `"Never synced"` unless Android's `HealthConnectClient` already holds real records for today.
2. **Real Health Connect Readback**: Confirm that granting Health Connect permissions immediately reads and displays real `StepsRecord`, `HeartRateRecord`, `SleepSessionRecord`, and `ActiveCaloriesBurnedRecord` aggregates from the device.
3. **Build & Unit Test Check**: Compile the APK and run Robolectric tests to verify the clean empty state, permission checklist, and M3 navigation flows.
