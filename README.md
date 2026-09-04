# XDown - X Media Downloader

Professional Android application for downloading media from X (formerly Twitter).

## Features

- **Media Downloading**: Download photos, videos, and GIFs from X
- **Quality Selection**: Choose from multiple quality options
- **URL Support**: Paste tweet URLs directly
- **Username Support**: Enter @username to browse media
- **Clipboard Support**: One-tap paste from clipboard
- **Professional UI**: Material 3 design with Jetpack Compose
- **Download Progress**: Real-time progress with notifications
- **Foreground Service**: Background downloading
- **Download History**: Track all downloads
- **Splash Screen**: Animated splash screen
- **Settings Screen**: App configuration

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Networking | OkHttp |
| Image Loading | Coil |
| JSON Parsing | Gson |
| HTML Parsing | Custom + OkHttp |
| Persistence | DataStore |

## Project Structure

```
app/src/main/java/com/xdown/app/
├── MainActivity.kt
├── XDownApplication.kt
├── data/
│   ├── model/          # Data models
│   ├── remote/         # Network services
│   └── repository/     # Data repositories
├── domain/usecase/     # Business logic
├── di/                 # Hilt DI modules
├── ui/
│   ├── theme/          # Colors, Typography
│   ├── screens/        # App screens
│   ├── components/     # Reusable components
│   └── navigation/     # Navigation graph
└── util/               # Utility classes
```

## Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34

### Build

1. Clone the repository
2. Open in Android Studio
3. Wait for Gradle sync
4. Run on device or emulator (API 26+)

### Command Line Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

## Permissions

| Permission | Purpose |
|------------|---------|
| INTERNET | Fetch media from X |
| ACCESS_NETWORK_STATE | Check connectivity |
| READ/WRITE_EXTERNAL_STORAGE | Save downloads (Android < 13) |
| READ_MEDIA_IMAGES/VIDEO | Access downloads (Android 13+) |
| POST_NOTIFICATIONS | Download progress |
| FOREGROUND_SERVICE | Background downloads |
| WAKE_LOCK | Keep device awake during download |

## How It Works

1. **Input**: User enters a tweet URL or @username
2. **Fetch**: App scrapes media using multiple APIs (fxtwitter, vxtwitter, HTML)
3. **Parse**: Extracts all available media and quality options
4. **Select**: User chooses quality from bottom sheet
5. **Download**: Media is saved to Downloads/XDown folder
6. **Track**: Progress shown in notifications and UI

## Download Location

All downloads are saved to:
```
/Downloads/XDown/
```

File naming: `{TYPE}_X_{ID}_{timestamp}.{ext}`
- Images: `IMG_X_{id}_{time}.jpg`
- Videos: `VID_X_{id}_{time}.mp4`
- GIFs: `GIF_X_{id}_{time}.gif`

## License

MIT License
