# MindVault - Study Companion App

MindVault is an Android study companion app designed to help you maintain focus during study sessions through an uninterruptible focus mode.

## Features

### 🎯 Focus Mode
- **Uninterruptible Sessions**: Once started, focus mode cannot be closed or interrupted
- **Customizable Duration**: Choose from 15, 25, 30, 45, 60, 90, or 120-minute sessions
- **Time-based Restrictions**: Automatically prevents focus sessions during sleep hours
- **Persistent Notifications**: Shows remaining time and motivational messages
- **Full-screen Lock**: Prevents task switching and app closing during sessions

### ⏰ Time Management
- **Sleep Time Protection**: Focus mode is disabled from 2:40 PM to 10:00 PM
- **Smart Session Detection**: Automatically detects current time period (Study/Sleep)
- **Real-time Clock**: Beautiful home screen with current time display

### 🎨 Modern UI
- **Material Design 3**: Clean, modern interface with gradient backgrounds
- **Responsive Layout**: Optimized for different screen sizes
- **Intuitive Navigation**: Simple, user-friendly interface
- **Visual Feedback**: Color-coded session types and status indicators

## How It Works

1. **Home Screen**: View current time, session type, and access focus mode
2. **Duration Selection**: Choose your preferred study session length
3. **Focus Session**: Enter uninterruptible focus mode with timer and motivational content
4. **Session Completion**: Automatic completion notification and return to home

## Technical Features

- **Foreground Service**: Keeps focus session running even if system tries to kill the app
- **Wake Lock**: Prevents device from sleeping during focus sessions
- **System Alert Window**: Maintains app visibility over other applications
- **Task Affinity**: Prevents focus mode from being closed through recent apps

## Time Restrictions

- **Study Time**: Available from 10:00 PM to 2:40 PM (next day)
- **Sleep Time**: 2:40 PM to 10:00 PM (focus mode disabled)
- **Automatic Detection**: App automatically detects current time period

## Future Features

The app is designed to be expandable with additional study companion features:
- Study statistics and analytics
- Break reminders
- Study goal tracking
- Productivity insights
- Custom study schedules

## Installation

1. Clone the repository
2. Open in Android Studio
3. Build and run on your Android device
4. Grant necessary permissions for optimal functionality

## Permissions Required

- `SYSTEM_ALERT_WINDOW`: For overlay functionality
- `FOREGROUND_SERVICE`: For persistent focus sessions
- `WAKE_LOCK`: To keep device awake during sessions
- `DISABLE_KEYGUARD`: For focus mode lock screen
- `REORDER_TASKS`: For task management

## Minimum Requirements

- Android API Level 35+
- Kotlin support
- Jetpack Compose

## Architecture

- **MVVM Pattern**: Clean separation of concerns
- **Jetpack Compose**: Modern UI toolkit
- **Coroutines**: Asynchronous programming
- **Foreground Services**: Background processing
- **Material Design 3**: Modern design system

---

**Note**: This app is designed to be a serious study tool. Once a focus session starts, it cannot be interrupted until completion. Use responsibly and ensure you have adequate time for your selected session duration.
