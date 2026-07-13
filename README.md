# Alarm Clock

[![Tests](https://github.com/DaiCapra/alarm-clock/actions/workflows/tests.yml/badge.svg)](https://github.com/DaiCapra/alarm-clock/actions/workflows/tests.yml)
[![Build](https://github.com/DaiCapra/alarm-clock/actions/workflows/build.yml/badge.svg)](https://github.com/DaiCapra/alarm-clock/actions/workflows/build.yml)

An Android alarm clock app meant to be simple without ads and bloat.

## Features

- Create, edit, and toggle alarms
- Snooze with volume buttons

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

