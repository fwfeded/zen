<div align="center">

# Zen · 禅

**Fewer distractions. More room to focus.**

A minimal Android home screen with essential apps nearby and secondary tools tucked away.

[Download APK](https://github.com/fwfeded/zen/releases/latest) · [Report an issue](https://github.com/fwfeded/zen/issues/new/choose) · [简体中文](README.md)

**Android 10+ · Personal test release · No account required**

</div>

<p align="center">
  <img src="docs/images/water.png" width="240" alt="Zen home screen with a light mountain landscape" />
  <img src="docs/images/forest.png" width="240" alt="Zen home screen with a green forest theme" />
  <img src="docs/images/night.png" width="240" alt="Zen home screen in its dark theme" />
</p>

Actual app screenshots with example weather. The interface supports English, Simplified Chinese and Traditional Chinese. Other system languages fall back to English; quotations and user content are not automatically translated.

## Why I am building this

My phone has slowly become a second home. But the constant stream of information and the growing collection of apps also keep taking my time and attention. I started Zen to take some of that back: keep useful tools close, let everything else wait until I choose to look for it, and leave a little space to remember why I picked up my phone.

This is a personal, non-commercial project. I hope to publish the application source after preparing it properly. [Developer notes, in Chinese](DEVELOPER_NOTES.md).

## What it does

- Keep up to four essential apps on the home screen, optionally collapsed. Find other apps by searching; an empty search shows no recommendations.
- Start a focus timer without entering a goal. Customize focus and rest durations using hours, minutes or seconds.
- Manage daily tasks and view daily focus bars with weekly and monthly totals.
- Add goal reminders only for selected apps, based on foreground usage time.
- Choose six built-in themes with day/night and weather variations.
- Optionally show priority notifications inside Zen. Android's notification shade is unchanged.

## Install and update

Download `zen-30.apk` for version 0.5.5 from [Releases](https://github.com/fwfeded/zen/releases/latest). Install it as a regular app first; making it your default home app is optional. The automatically generated **Source code** archives are repository documents, not an installable app.

To return to your previous launcher, open Zen's home/exit settings and use its return-to-previous-launcher action. You do not normally need to uninstall Zen.

Version 0.5.5 includes the update feed. Update checks happen on return to Zen, at most once every 24 hours. Downloads require a tap and Android asks before installation. Existing installations with a custom or explicitly cleared feed keep that choice.

## Privacy and limitations

Tasks, goals, focus records and preferences stay on the device. There is no account, cloud sync, advertising SDK or analytics SDK. Weather requests send location coordinates to Open-Meteo; update downloads contact GitHub. Android's geocoder may contact its configured provider to resolve a city name.

This is a personal test project. Emulator tests do not establish compatibility with every Android device. Weather differs from manufacturers' weather apps; split-screen usage attribution and device-wide battery savings are not guaranteed. Uninstalling the app or clearing its data removes local records.

**External theme cards are not distributed in this release.** Only six built-in themes are included.

## Repository status

This repository currently contains releases, documentation and issue tracking. **The application source is not yet published, and no project-wide open-source license has been selected.** Source publication will follow a separate review. See [project status](PROJECT_STATUS.md), [privacy](PRIVACY.md), [contributing](CONTRIBUTING.md) and [third-party notices](THIRD_PARTY_NOTICES.md). Detailed guides are currently in Chinese; English feedback is welcome.
