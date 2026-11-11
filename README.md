# FitifySync: Root-Powered Fitify to Health Connect Bridge

**FitifySync** is an Android application designed for rooted Android devices (Magisk, KernelSU, APatch) that extracts training sessions, exercise history, and caloric burn data from the **Fitify** app (`com.fitifyworkouts.bodyweight.workoutapp`) and bridges it directly into **Android Health Connect**.

Through Health Connect, your Fitify workouts become seamlessly available in:
* **Samsung Health** (full workout breakdown, calorie counting, and weekly targets)
* **Google Fit** (Heart Points, move minutes, workout logs)
* **Third-Party Fitness Tools** (Strava, Intervals.icu, Google Sheets, Jupyter/Pandas)

---

## 🏗 System Architecture

```
┌────────────────────────────────────────────────────────┐
│ Fitify App (com.fitifyworkouts.bodyweight.workoutapp)  │
│ Internal SQLite DB: /data/data/.../databases/fitify.db │
└───────────────────────────┬────────────────────────────┘
                            │ (1) Root 'su' Safe Dump
                            ▼
┌────────────────────────────────────────────────────────┐
│ FitifySync Engine (com.fitifysync.app)                 │
│  ├─ RootRunner: Non-destructive copy & WAL handling    │
│  ├─ FitifyDatabaseParser: Dynamic SQLite inspector     │
│  ├─ HealthConnectManager: Batch & deduplication engine │
│  └─ SyncWorker: Periodic WorkManager background sync   │
└───────────────────────────┬────────────────────────────┘
                            │ (2) ExerciseSession & Calories
                            ▼
┌────────────────────────────────────────────────────────┐
│ Android Health Connect (Google Standard)               │
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
               ▼                          ▼
     ┌──────────────────┐       ┌──────────────────┐
     │  Samsung Health  │       │    Google Fit    │
     └──────────────────┘       └──────────────────┘
```

---

## 🚀 Key Features

1. **Non-Destructive Root Extraction**: Safely copies Fitify's private database along with `-wal` and `-shm` write-ahead logs to avoid database locking or corruption.
2. **Schema-Agnostic Parser**: Automatically inspects table names (`workout_history`, `history`, `completed_workouts`) and columns using `PRAGMA table_info`, making it resilient across past and future Fitify updates.
3. **Idempotent Health Connect Sync**: Attaches deterministic `clientRecordId` hashes (`fitify_{session_id}`) so syncing multiple times never generates duplicate workouts.
4. **Health Connect Compliant**: Native integration with `ExerciseSessionRecord` (mapped to calisthenics, HIIT, strength training, stretching) and `ActiveCaloriesBurnedRecord`.
5. **Automated Background Sync**: Optional WorkManager schedule running every 6 hours so your workouts stay updated without opening the app.
6. **Data Export for Analysis**: Built-in 1-tap CSV export for in-depth personal fitness analytics using Excel or Python pandas.

---

## 📱 How to Build and Install

### Prerequisites
* Android Studio (Koala, Jellyfish, or newer)
* JDK 17
* Android SDK 34 (minSdk 28)
* Rooted Android phone (Magisk, KernelSU, or APatch)
* **Health Connect** installed (pre-installed on Android 14+; available via Play Store on Android 9–13)

### Build APK via Terminal
```bash
cd FitifySyncApp
./gradlew assembleDebug
```
The compiled APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Configuration & Linking

### 1. Root Authorization
1. Open **FitifySync**.
2. When prompted by Magisk, KernelSU, or APatch, select **Grant** (or "Always Allow").
3. The app status indicator will turn green: `Root Access: Granted`.

### 2. Health Connect Authorization
1. Tap **Authorize** on the Health Connect card.
2. Select **Allow All** for:
   * Exercise (Read & Write)
   * Active Calories Burned (Read & Write)
   * Total Calories Burned (Write)

### 3. Link Samsung Health
1. Open the **Samsung Health** app.
2. Tap the **Three Dots Menu (⋮)** → **Settings**.
3. Tap **Health Connect**.
4. Enable **Allow all permissions** for Samsung Health to read and write.
5. Your Fitify workouts synced by FitifySync will now appear inside Samsung Health workouts!

### 4. Link Google Fit
1. Open the **Google Fit** app.
2. Tap **Profile** → **Settings (Gear Icon)**.
3. Under *Health Connect*, toggle **Sync Fit with Health Connect** to **ON**.
4. Workouts and active calories will automatically sync to your Google Fit dashboard.

---

## 🛠 Direct CLI / Termux Alternative
If you prefer running a direct Python script or Termux shell without compiling the Android app:
* **Python Tool**: Run `tools/fitify_cli_export.py` on your rooted device or PC over ADB.
* **Termux Shell**: Run `tools/termux_fitify_sync.sh` directly in Termux with root privileges.
