# Activity Tracker

[![Android CI](https://github.com/coconutshell/activity-tracker-android/actions/workflows/buildapk.yml/badge.svg)](https://github.com/coconutshell/activity-tracker-android/actions/workflows/buildapk.yml)


A local-first native Android Kotlin app for recording what actually happened over time.

## Identity
- **App name:** Activity Tracker
- **Application ID:** `com.coconutshell.activitytracker`
- **Core interaction:** See a thing → tap it → record it → continue

## Stack
- Kotlin 2.4.20
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose / Material 3
- Compose BOM 2026.08.00
- Room 2.8.5
- ViewModel + Coroutines / Flow
- DataStore for reminder settings

## Features
- Create any Thing; no predefined activity catalog.
- Record historical occurrences with actual date/time and quantity.
- Search Things.
- Optional Libraries and Library filtering.
- Thing history with occurrence editing/deletion.
- Morning/night reminder settings.
- Export the full activity history to `ActivityTracker.zip` containing Markdown files.
- Custom launcher icon.

## GitHub Actions
`.github/workflows/buildapk.yml` is intentionally failure-preserving.

It runs unit tests, lint, and APK build independently. Each stage writes a log and exit code. Regardless of failure, the workflow collects Gradle diagnostics, creates:

`activity-tracker-build-reports.zip`

and uploads it with:

```yaml
if: always()
```

Only after the report artifact has been uploaded does the workflow fail the job when a verification stage failed.

This implements the supplied requirement that reports remain available even when the APK is not produced.

## Build
The CI environment uses JDK 17 and Gradle 9.6.0. With Android SDK tooling installed locally, the normal command is:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

The debug APK is produced under:

`app/build/outputs/apk/debug/`

## Repository notes
- Repository name: `activity-tracker-android`
- Package/application ID: `com.coconutshell.activitytracker`
- Generated APKs, Gradle build output, CI logs, and ZIP archives are intentionally ignored by Git.
- CI publishes the APK when available and always publishes the diagnostic report artifact, including when tests, lint, or the build fail.
- The repository does not commit machine-specific `local.properties` or IDE metadata.

## Verification note
The generation environment did not have the Android SDK or a Gradle executable, so the APK was not locally compiled here. The project files and CI configuration were statically checked. GitHub Actions is configured to perform the actual Android build and preserve diagnostics on failure.
