# Contributing to FocusForge

Thank you for your interest in contributing to FocusForge! This project is dedicated to building a privacy-first, free-and-open-source digital sanctuary for students and deep workers.

## Code of Conduct & Core Tenets

1. **Local-First & Privacy First**: Under no circumstances will code adding remote tracking, accounts, analytics, or third-party proprietary SDKs be accepted.
2. **Safety First**: Android features that completely brick or trap the user without access to Emergency calls/SMS/Settings will be rejected.
3. **Free & Open Source**: The project is licensed under **GPLv3**. All contributions must be compatible with GPLv3. Avoid proprietary Google Play-only APIs (e.g., Play Integrity, Google Sign-In).

## Getting Started

1. Fork the repository and create your feature branch from `main`:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. Ensure you have JDK 17/21 and Android SDK installed.
3. Keep code clean, formatted, and idiomatically Kotlin.
4. If writing code that works around Android device vendor quirks (e.g., Xiaomi/Samsung battery management, foreground service limits), add explanatory comments in code.

## Submitting Pull Requests

- Keep PRs focused on a single change or feature.
- Verify that `./gradlew test` and `./gradlew assembleDebug` pass.
- Provide clear testing instructions in your PR description.
