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
5. If Samsung Health supports importing these Health Connect workout records, they can appear in its workout history.

### 4. Link Google Fit
1. Open the **Google Fit** app.
2. Tap **Profile** → **Settings (Gear Icon)**.
3. Tap **Manage Health Connect settings** and ensure read access is enabled.
4. Under *Health Connect*, toggle **Sync Fit with Health Connect** to **ON**.
5. Display in Google Fit or Samsung Health depends on that app supporting and reading the exported record types.

---

## 📋 Health Connect Notes Payload (`fitifysync.exercise_segments`)

To allow downstream fitness loggers and AI workout analysts (such as [RepForge](https://github.com/Devasy/RepForge)) to recover exact movement names, exercise codes, and variant details that would otherwise be flattened into generic Health Connect segment types (e.g. `WEIGHTLIFTING`), FitifySync serializes a structured JSON payload into `ExerciseSessionRecord.notes` alongside the human-readable summary.

### Example Notes Payload:
```text
Fitify Morning Strength | Exercises: 2

{"schema":"fitifysync.exercise_segments","version":1,"exercises":[{"segment_index":1,"code":"bo233_push_ups","name":"Push-Ups","timing_kind":"reconstructed"},{"segment_index":3,"code":"bo009_squats","name":"Squats","timing_kind":"reconstructed"}]}
```

### Schema Specification:
* `schema`: `"fitifysync.exercise_segments"`
* `version`: integer (currently `1`)
* `exercises`: array of exercise segment objects:
  * `segment_index`: `int` (0-based index corresponding to the segment in `ExerciseSessionRecord.segments`, accounting for rest/pause segments)
  * `code`: `string` (original Fitify movement code, e.g. `push_up`, `bo091_front_neck_stretch`)
  * `name`: `string` (catalogue title when available; otherwise the parser's code-derived display label, preserved including Unicode)
  * `timing_kind`: `"reconstructed"` (indicates segment start/end boundaries were reconstructed from session timestamps/durations rather than live hardware sensor triggers)

Downstream readers must not treat reconstructed segment boundaries as measured when correlating heart-rate samples with exercises.

### AndroidX API compatibility
This app currently compiles against `connect-client:1.1.0-alpha11`. The current [ExerciseSegment reference](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/ExerciseSegment.md.txt) also describes `weight`, `setIndex` and `rateOfPerceivedExertion`, added in `1.2.0-alpha06`. Those newer fields are not written by this app. The source parser currently has no verified weight or RPE decoder; missing values must not be invented. RPE is the user's perceived exertion, not a catalogue's per-muscle resistance/stretch score.

The [session reference](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/ExerciseSessionRecord.md.txt) requires segment intervals to stay inside the session and not overlap. The writer constructs sequential intervals and extends the session end when reconstructed segments require it. `segment_index` is this payload's position in the full segment list, including rests; it is not the newer native `setIndex`. Neither record provides a field for Fitify movement codes or per-muscle intensity maps, so downstream consumers resolve codes against their exercise catalogue.

The related [Metadata](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Metadata.md.txt) and [HealthConnectClient](https://developer.android.com/reference/kotlin/androidx/health/connect/client/HealthConnectClient.md.txt) contracts govern identity/version updates and transactional inserts. Session and calorie records for one workout stay together when retrying a failed batch. These JVM checks do not establish end-to-end behavior on a device or another app's import support.

### Health Connect Notes Safety:
The [Android ExerciseSessionRecord API](https://developer.android.com/reference/androidx/health/connect/client/records/ExerciseSessionRecord) describes optional notes; it does not document a 1000-character notes limit. The [AndroidX implementation](https://github.com/androidx/androidx/blob/androidx-main/health/connect/connect-client/src/main/java/androidx/health/connect/client/records/ExerciseSessionRecord.kt) does not impose that limit either.

FitifySync uses its own **64 KiB UTF-8 notes budget** and **256 KiB estimated batch budget** to keep individual writes bounded. These are application budgets, not Android API limits. Every exercise identity and repeated set is retained. Only the readable summary may be shortened, without splitting Unicode code points. If the complete payload or an individual workout cannot fit the budget, that workout fails explicitly and stays pending; identities are never silently dropped.

An empty workout includes `exercises: []`. Every listed item requires a unique nonnegative `segment_index`, string `code` and `name`, and `timing_kind: "reconstructed"`. Empty source codes/names remain empty for downstream review; consumers must not guess an identity. The parser validates schema/version and required fields rather than searching for a schema substring. Existing incomplete payloads are rebuilt from the source. Newer unsupported schema versions and existing notes above the reader budget are left untouched and reported.

Normal manual and background sync use stable `fitify_<source id>` client IDs and higher client record versions, following [Health Connect upsert guidance](https://developer.android.com/health-and-fitness/health-connect/write-data). Existing-record lookups are restricted to FitifySync's own data origin and paginated. Complete equivalent session/segment data and expected active-calorie companions are skipped; updates use a version higher than both records. Normal sync does not delete and recreate records.

Writes are bounded batches. If a batch fails, retries keep each workout's session and calorie records together. A failed workout construction does not discard earlier queued workouts. New, updated, already-current and failed counts are separate, and a workout is marked synced only after confirmed insertion. A failed existing-record read prevents writes rather than guessing record versions. Writing to Health Connect alone does not guarantee display in any other app.

Existing-record scans have an open lower time bound, independent of new source timestamps, so corrections can find earlier exports. Missing or invalid calorie estimates preserve the existing companion; an explicit valid zero removes stale calories after the session write, before reporting success. Background sync retries transient failures, reports permanent preparation/source failures without immediate retry loops, and cleans its temporary dump even after parser rejection.

The supported session source is Fitify's modern Firestore cache. Unverified legacy layouts fail explicitly: the previous placeholder fallback that invented current timestamps, 15-minute durations and 100-calorie workouts has been removed. A real legacy decoder requires a verified schema and fixture.



---

## 🛠 Direct CLI / Termux Alternative
If you prefer running a direct Python script or Termux shell without compiling the Android app:
* **Python Tool**: Run `tools/fitify_cli_export.py` on your rooted device or PC over ADB.
* **Termux Shell**: Run `tools/termux_fitify_sync.sh` directly in Termux with root privileges.
