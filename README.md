# FocusForge

> Free, open-source (GPLv3), local-first Android app designed to block pornography and doom-scrolling apps, plan deep-work time blocks (per Cal Newport's *Deep Work*), and track exam preparation progress.

---

## Philosophy and Principles

- **100% Local-First**: No accounts, no backend servers, no telemetry, no analytics, no network permissions required for core functionality. Your study habits and history never leave your device.
- **Safety Guarantee**: Never locks you out of your device. Phone calls, SMS, the system dialer, system settings, and an emergency bypass are always accessible.
- **F-Droid and Sideload Ready**: Zero dependency on proprietary Google Play Services. Builds strictly with free and open-source tooling.
- **GPLv3 Licensed**: Free software for everyone, forever.

---

## Features (Phase 1 Status)

- **Focus Timer**: Pomodoro or custom duration timers.
- **Foreground Service**: Persistent foreground service keeps timers running accurately even if the app process is closed or battery optimization kicks in.
- **Session Log**: Record each study block with Subject, Topic, Duration, Focus Rating (1-5), and Type (`DEEP` vs `SHALLOW`).
- **Local Persistence**: Powered by SQLite and Room, reactively updating the UI via Kotlin Coroutines `StateFlow`.
- **Modern Jetpack Compose UI**: Clean Material 3 design with light and dark theme support.

---

## Architecture

FocusForge is built using modern Android architecture guidelines:

```
UI Layer (Jetpack Compose + Material 3)
   ▲               ▲               ▲
   │               │               │
HomeViewModel  TimerViewModel  HistoryViewModel
   ▲               ▲               ▲
   │               ▼               │
   │          TimerService         │
   │               │               │
   └─────────► SessionRepository ◄─┘
                   │
                   ▼
               Room (SQLite)
```

- **Pattern**: MVVM (Model-View-ViewModel) with unidirectional data flow (UDF).
- **Dependency Injection**: Hilt.
- **Concurrency**: Kotlin Coroutines & Flow.
- **Database**: Android Jetpack Room with custom TypeConverters.

---

## Building From Source

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 34, Build Tools 34.0.0+)
- Gradle 8.7+ (or use `./gradlew`)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/your-username/FocusForge.git
cd FocusForge

# Build debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk

# Install onto a connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Testing Phase 1 on Your Device

1. Install the debug APK via ADB or sideload it onto your phone.
2. Grant **Notification permission** when prompted (required on Android 13+ to display the timer countdown).
3. **Timer Screen**:
   - Enter a Subject (e.g. *Physics*) and Topic (e.g. *Thermodynamics*).
   - Select **Deep Work** or **Shallow Work**.
   - Tap preset buttons (25m, 50m) or choose custom minutes, then tap **Start**.
   - Press the Home button or swipe the app away. Notice the persistent notification updating every second.
   - When the timer finishes, a completion dialog prompts for a **1-5 Star Focus Rating**.
4. **History Screen**:
   - Review your logged sessions in reverse chronological order.
5. **Home Screen**:
   - Check today's Deep Work vs Shallow Work summary and total focused time.

---

## License

FocusForge is licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](file:///home/vijay/Documents/Projects/FocusForge/LICENSE) for details.
