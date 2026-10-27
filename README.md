# Dreamcatcher

An Android alarm clock that turns into a dream journal the moment you wake up.

When an alarm rings, a full-screen alarm opens (over the lock screen too). Tap **Stop alarm & record dream** and the
alarm goes quiet. The phone then starts listening right away, so you can say what you dreamed before it fades. You can
also type. Each entry is saved to a journal you can search, filter by tag, edit, and export.

## Features

- **Alarms**: one-time or repeating on chosen weekdays, with labels, vibration, a 9-minute snooze, and a sound that
  fades in gently. Alarms survive reboots and time-zone changes, and a ringing alarm stops on its own after 10 minutes.
- **Wake-up capture**: stopping the alarm opens the dream screen with voice dictation already running. Two styles,
  switchable in Settings (gear icon):
  - **Voice first** (default): a nearly empty screen with a big mic. Your words appear in large type as you talk. Tap
    **I'm done** to review the text and tag it.
  - **Guided questions**: prompts for *What happened?*, *Who was there?*, *Where were you?* and *How did it feel?*,
    each with its own mic. The answers are combined into one entry.

  Entries save automatically, even if the screen turns off.
- **Tags**: mark each night as **Dream**, **Nightmare** or **No dream** (pick one), and add **Lucid** or
  **Recurring** to a remembered dream. **+ New tag** makes your own tags (feelings, themes, sleep notes). They're
  offered on every entry after that, most used first. A **No dream** entry can be saved with no text, so nights you
  don't remember still count.
- **Woke up before the alarm?** Start a recording from the lock screen without unlocking. Use the **Record dream**
  Quick Settings tile, or turn on the silent lock screen shortcut notification. Both are set up from the card at the
  bottom of the Alarms tab.
- **Color schemes**: 17 to choose from in Settings, 11 dark and 6 light. They include True black for OLED screens,
  plus Red light and Amber glow, which are easy on night-adjusted eyes. A unit test checks every scheme's text against
  WCAG AA contrast.
- **Privacy on the lock screen**: only the new-entry screen shows over the lock screen. The journal needs an unlocked
  phone.
- **Journal**: newest first, with search, a tag filter, and editing. **Share** exports the whole journal as plain text.

## Building

Requirements: JDK 17 and the Android SDK (API 35). Android Studio sets both up for you.

```sh
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
```

Every push runs the **Android build** GitHub Actions workflow. Download a ready-to-install APK from the
`dreamcatcher-debug-apk` artifact on the workflow run.

## First run

Open the **Alarms** tab. A card there lists anything the app still needs, with an **Allow** button for each:

- **Notifications**: needed to show the alarm when it rings.
- **Full-screen alarms** (Android 14+): lets the alarm open over the lock screen.
- **Alarms & reminders**: rings at exactly the right time. This is normally granted automatically.
- **Microphone**: for speaking your dream. Typing always works without it.

Speech recognition uses the phone's built-in recognizer (usually Google's). Some phones need a network connection for it.

## Project layout

```
app/src/main/java/com/garza/dreamcatcher/
  alarm/   AlarmTime (time math), AlarmScheduler, AlarmReceiver, BootReceiver,
           AlarmService (sound + notification), AlarmRingActivity (full-screen alarm)
  dream/   DreamEntryActivity + screen + view model, SpeechDictation, DreamText helpers
  data/    Room database: Dream and Alarm entities and DAOs
  ui/      MainActivity with the Journal and Alarms tabs, theme
```
