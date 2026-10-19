# Appointments

A small Android app for personal appointments (health, car, home, admin, family).
Each appointment is written straight into your phone's Google Calendar, with a reminder,
so it syncs everywhere and you get normal phone notifications.

## How the code is organised

| File | What it does |
|---|---|
| `app/src/main/java/.../Model.kt` | What an appointment is, and saving the list on the phone |
| `app/src/main/java/.../CalendarSync.kt` | Writing, updating and deleting events in the phone's calendar |
| `app/src/main/java/.../MainActivity.kt` | All the screens: list, add/edit form, calendar picker |
| `app/src/main/AndroidManifest.xml` | App name, icon, and the calendar permission |
| `app/build.gradle.kts` | Build settings and libraries |
| `.github/workflows/build.yml` | Lets GitHub build the .apk for you |

## Build it

**Option A: GitHub (no installs).** Upload this folder to a new GitHub repository.
GitHub builds the app automatically. When the build finishes (about 5 minutes), open the
repository's **Releases** page on your phone and download `app-release.apk`.

**Option B: Android Studio.** Open this folder in Android Studio, connect your phone with a USB
cable (with USB debugging on), and press Run.

## Notes

- `app/debug.keystore` signs the app. Keep it in the project so new versions install over old ones.
- Built with Kotlin and Jetpack Compose. Minimum Android 8.0.
