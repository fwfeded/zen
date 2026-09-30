<div align="center">

# Zen · 禅

### Keep your time and attention for what you mean to do.

A quiet second home screen for Android.<br>
Essential tools stay close. Everything else appears when you need it.

[**Download**](https://github.com/fwfeded/zen/releases/latest) · [**User guide (中文)**](docs/USER_GUIDE.md) · [**Report an issue**](https://github.com/fwfeded/zen/issues/new/choose) · [**简体中文**](README.md)

Android 10+ &nbsp; / &nbsp; No account required &nbsp; / &nbsp; Personal test release

</div>

<p align="center">
  <img src="docs/images/water.png" width="30%" alt="Zen with a pale blue landscape and a focus timer" />
  <img src="docs/images/forest.png" width="30%" alt="Zen with a green landscape and a collapsed app dock" />
  <img src="docs/images/night.png" width="30%" alt="Zen with a dark blue night landscape" />
</p>
<p align="center"><sub>Actual 0.5.5 app screenshots with example weather. <a href="docs/THEMES.md">View all six themes →</a></sub></p>

## Why Zen exists

My phone has slowly become a second home. It is where I talk to people, learn, look things up and take a break. But the constant stream of information and the growing collection of apps also keep taking my time and attention. I started Zen to take some of that back.

Picking up a phone often begins with a specific intention: answer a message, find a reference, or write down an idea. The home screen can make that intention easier to follow—or place many other possibilities in its way.

Zen changes what appears first. Keep a few necessary tools nearby, search deliberately for other apps, and open secondary features only when needed. You can use it as a regular app or make it your default launcher.

This is a personal, non-commercial project, shaped by everyday use and feedback. Application source is now available under GPL-3.0-only. [Read the developer notes in Chinese](DEVELOPER_NOTES.md).

## A day with Zen

| Your intention | A path through Zen |
| --- | --- |
| Finish one small task | Add a daily task, start a focus session from it, and check it off when the work is done |
| Look something up without losing track | Search for the app; optionally set an opening goal and receive a reminder based on foreground usage |
| Read with fewer interruptions | Start the timer and enable the simplified focus view; pause or leave whenever needed |
| Review time spent | View daily bars and weekly or monthly focus totals |

Start with one feature. You do not need a plan, an account or every permission enabled before your first session.

## Features

### A home screen with room to breathe

Keep up to four essential apps on the home screen, optionally behind a small expandable dock. Find other apps by name; an empty search does not show recommendations. Time, weather, a short quotation and the focus control form the main scene.

There is a dedicated route back to your previous launcher. Trying Zen does not require making it your permanent default.

### Focus timing that fits the task

Tap the home-screen focus control to start, pause or resume. Long-press to open its settings. Focus and rest durations are independently customizable from one second to 24 hours; each input switches between hours, minutes and seconds. Save frequently used combinations as presets, then edit or remove them later.

A session can start without a written goal, or from a daily task. Optionally record a next step for later. Rest and the following session begin when you choose; the timer does not run an automatic cycle indefinitely.

### Tasks and a useful record of time

Add daily tasks, check them off, undo a deletion, or move older unfinished work to today. Timer completion never marks a task complete on your behalf.

An optional cross-app task overlay keeps one unfinished task available while using other apps. It can be expanded, moved or temporarily hidden and requires overlay and notification access.

Daily focus bars show weekly and monthly totals in compact forms such as `2h50min`. Only elapsed focus-stage time counts: pauses, rest and app-goal usage are excluded. Ending early keeps the time already recorded; a session crossing midnight is split between dates.

### Reminders for selected apps

The home-screen app list and reminder list are independent. Apps outside the reminder list open directly. When opening a selected app through Zen, set an intention and a reminder interval. Usage access allows time to accumulate while that app is in the foreground, pausing when you switch apps or lock the screen.

Priority notifications are filtered **inside Zen only**. Android's notification shade remains under system control; Zen does not silently revoke other apps' permissions.

### Themes, weather and your own words

Six built-in themes offer different palettes and landscapes, with day/night settings and optional motion. Weather can use an authorized location or a manually selected city. Manage quotations by theme, time of day, weather and category; add, edit, remove or restore them.

The interface supports Simplified Chinese, Traditional Chinese and English, with a system-language option. Other system languages fall back to English. Tasks, quotations and user content are not automatically translated.

**External theme cards are not distributed in this release.** The APK includes only the original six built-in themes.

## Install, try and return

1. Download `zen-30.apk` for version 0.5.5, about 22.1 MiB, from [Releases](https://github.com/fwfeded/zen/releases/latest).
2. Follow Android's installation prompts. Open Zen as a regular app first; becoming the default launcher is optional.
3. If desired, use Zen's home/exit settings to enable it as the default home app. To return, use the action that names your previous launcher on the same page.

Source archives are not installable APKs. Use the current repository for the complete source and follow [BUILDING.md](BUILDING.md). The historical v0.5.5 tag predates source publication. Existing installations with the same signature can be updated in place; uninstalling first removes local data.

## Updates

Version 0.5.5 includes the official update feed. On returning to Zen, automatic checks happen at most once every 24 hours. A download requires your tap and installation requires Android's confirmation. Custom or explicitly cleared feed settings are preserved.

For 0.5.4, enter this address in the update settings once:

```text
https://github.com/fwfeded/zen/releases/latest/download/latest.json
```

Downloads are checked for package identity, increasing version, size, SHA-256 and signing certificate. [Update guide, in Chinese](docs/UPDATES.md).

## Privacy, verification and limits

Tasks, goals, focus records, quotations and preferences stay on the device. There is no account, cloud sync, advertising SDK or analytics SDK. Weather requests send location coordinates to Open-Meteo; update requests contact GitHub. Android's geocoder may contact its configured provider to resolve city names.

Version 0.5.5 passed 154 unit tests, 13 emulator update tests and nine release-tool tests. An Android 15 emulator also completed a real GitHub download and system upgrade from 0.5.4 to 0.5.5, preserving tasks, focus totals and settings. Physical devices still require verification; this remains a personal test release.

Weather can differ from the manufacturer's app. Split-screen usage attribution is not guaranteed. Reminder delivery depends on Android permissions and system settings. Battery-saving options reduce some of Zen's work, but no device-wide battery-life improvement has been demonstrated. Uninstalling or clearing data removes local records; full export/restore is not available yet. GitHub downloads depend on network availability.

## Documentation and participation

Detailed guides are currently in Chinese. English issue reports are welcome.

| Topic | Document |
| --- | --- |
| First session and switching launchers | [Getting started](docs/GETTING_STARTED.md) |
| Controls and workflows | [User guide](docs/USER_GUIDE.md) |
| Themes and quotation editing | [Themes](docs/THEMES.md) |
| Troubleshooting | [FAQ](docs/FAQ.md) |
| Permissions and local data | [Permissions](docs/PERMISSIONS.md) · [Privacy](PRIVACY.md) |
| Future work and feedback | [Roadmap](ROADMAP.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) |

**Application source is now public under GPL-3.0-only.** Commercial use is permitted; distributing covered modifications requires compliance with GPL source and notice obligations. Third-party assets retain their own licenses. [Source](android/) · [Build guide](BUILDING.md) · [License](LICENSE) · [Dependency audit](docs/DEPENDENCIES.md). [Project status](PROJECT_STATUS.md) · [Third-party notices](THIRD_PARTY_NOTICES.md) · [Changelog](CHANGELOG.md).
