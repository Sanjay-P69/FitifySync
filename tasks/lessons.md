# Lessons Learned

## 2026-11-11 - Android ViewModel Reflection with Custom Constructors
- **Category**: Architecture / Lifecycle ViewModel
- **What happened**: When adding optional parameters (such as `repository: FitifySyncRepository`) to `MainViewModel(application: Application, ...)` with default values, `AndroidViewModelFactory` crashed with `NoSuchMethodException: <init> [class android.app.Application]` during activity creation because Java reflection could not find the single-argument constructor.
- **Rule to follow**: Always define an explicit secondary `constructor(application: Application) : this(application, DefaultFitifySyncRepository(application))` for ViewModels instantiated via default factories.

## 2026-11-11 - Multi-Location Database Extraction for Sideloaded & Restored Apps
- **Category**: Root / Storage
- **What happened**: Fitify's SQLite and Firestore cache databases on the test device were located under `/sdcard/Download` (`/data/media/0/Download`) from a previous restore/mod install, rather than solely inside `/data/user/0/com.fitifyworkouts.bodyweight.workoutapp/databases`.
- **Rule to follow**: When extracting databases via root, inspect both the internal application sandbox `/data/user/0/<pkg>/databases` and user media locations (`/sdcard/Download`) using non-recursive path lists so extraction works reliably across fresh installs and backup restores.

## 2026-11-11 - Health Connect ExerciseSessionRecord & ExerciseSegment Compatibility
- **Category**: Health Connect SDK Validation
- **What happened**: Instantiating `ExerciseSessionRecord` with specific session types like `EXERCISE_TYPE_STRETCHING` threw `IllegalArgumentException: segmentType and sessionType is not compatible` because non-universal session types restrict which `ExerciseSegmentType`s are permitted (e.g. stretching cannot contain plank or crunch segments).
- **Rule to follow**: Map multi-exercise circuit/bodyweight sessions to Health Connect's universal session types (`EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING` [36], `EXERCISE_TYPE_BOOT_CAMP` [10], or `EXERCISE_TYPE_OTHER_WORKOUT` [0]) and implement a fallback to `EXERCISE_TYPE_OTHER_WORKOUT` in a `try-catch` block. Universal session types permit all valid `ExerciseSegment`s while preserving the user-facing title and full exercise breakdown in Google Fit and Samsung Health.

## 2026-11-11 - Duration in Time-Based ExerciseSegments (Planks, Holds)
- **Category**: Health Connect Data Model
- **What happened**: Health Connect's `ExerciseSegment` does not have an integer `duration` field; it models duration strictly via `[startTime, endTime]`, with `repetitions = 0` for time-based exercises like planks.
- **Rule to follow**: Represent time-based exercises by setting `endTime = startTime.plusSeconds(durationSeconds)` and `repetitions = 0`.

## 2026-11-11 - Scaffold Inset Consumption & Android Lint
- **Category**: Jetpack Compose / Edge-to-Edge Lint
- **What happened**: In edge-to-edge screens with `WindowInsets(0)` or `WindowInsets.safeDrawing`, ignoring the `innerPadding` parameter of `Scaffold` (`{ _ -> ... }`) fails Android CI with `UnusedMaterial3ScaffoldPaddingParameter` (fatal lint error).
- **Rule to follow**: Always consume `innerPadding` in the child modifier (`Modifier.padding(innerPadding)`), or explicitly configure `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` so that insets and system bars are respected without manual padding calculations.

## 2026-11-11 - Canvas-Based Micro-Timelines for High-Volume LazyColumns
- **Category**: Jetpack Compose / Performance
- **What happened**: Emitting 20-30 layout nodes (`Row` + `Box`) per workout card in a 2,500+ item LazyColumn causes excessive layout allocations and measurement passes on scroll.
- **Rule to follow**: Render micro-timelines and multi-colored progress bars using a single `Canvas` in the draw phase, memoizing segment colors with `remember(items)`.
