# Alarm Clock

[![Tests](https://github.com/DaiCapra/alarm-clock/actions/workflows/tests.yml/badge.svg)](https://github.com/DaiCapra/alarm-clock/actions/workflows/tests.yml)
[![Build](https://github.com/DaiCapra/alarm-clock/actions/workflows/build.yml/badge.svg)](https://github.com/DaiCapra/alarm-clock/actions/workflows/build.yml)

An Android alarm clock app built with Kotlin.

## Tech Stack

- Kotlin
- Hilt (dependency injection)
- Room (persistence)
- Kotlin Coroutines
- AndroidX Lifecycle (ViewModel)
- Material Components

## Requirements

- JDK 17
- Android SDK 36 (min SDK 24)

## Building

```bash
./gradlew assembleDebug
```

## Tests

Unit tests run with JUnit and Robolectric:

```bash
./gradlew testDebugUnitTest
```

Instrumented tests (require a device or emulator):

```bash
./gradlew connectedAndroidTest
```

Test status is reported by the [Tests workflow](https://github.com/DaiCapra/alarm-clock/actions/workflows/tests.yml), which runs unit tests on every push and pull request to `main`.

## Builds

A debug APK is built on every push to `main` and published as a prerelease on the [releases page](https://github.com/DaiCapra/alarm-clock/releases). Only the 5 most recent builds are kept; older ones are deleted automatically.
