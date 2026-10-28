# Local Voice

**Private voice search for Android TV / Google TV.** Press the mic button on your remote, say what you want, and the words are typed into the search box on screen — Nuvio, Spotify, SmartTube, the Play Store, anything with a text field. Speech is recognized **on the TV box itself**. Nothing is sent to Google or anyone else; the app doesn't even have internet permission.

Built for people who turned off Google Assistant for privacy and lost their remote's mic button in the process.

> **Powered by [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)** from the [Next-gen Kaldi](https://github.com/k2-fsa) project and the **[Moonshine](https://github.com/moonshine-ai/moonshine)** speech model from Moonshine AI. The speech recognition itself — the engine and the model — is their open-source work (Apache-2.0 and MIT). Local Voice is the glue that connects it to your remote. Huge thanks to both teams; if this is useful to you, go give them a star.

## What it does

- **Remote mic button → text box.** Press the mic button (no need to hold it), speak, and when you stop talking the text appears in whatever search box is on screen and the search is submitted.
- **On-device speech recognition** using [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Next-gen Kaldi) running the [Moonshine](https://github.com/moonshine-ai/moonshine) base English model — accurate on titles and acronyms ("Law and Order SVU") and about a second to transcribe on a budget box.
- **Light when idle.** The model loads only when you press the button — while you're talking, so you don't wait for it — and unloads after 3 quiet minutes. Idle memory is ~12 MB.
- **No internet permission.** Android won't let the app send anything off the device. You can check: `Settings > Apps > Local Voice > Permissions` lists only Microphone.
- **Works with Google's voice search still installed** — Local Voice takes over the mic button through its accessibility service. (You can still disable Google's voice search for privacy; see below.)

## Compatibility

Tested on an **onn 4K Plus** (Google TV, Android 14). It should work on other Android TV / Google TV devices running Android 7+, but that's untested — reports welcome (open an issue with your box and whether it worked).

Two things have to be true on your box:

- **The mic button has to reach apps.** Standard mic buttons (`SEARCH`, `VOICE_ASSIST`, `ASSIST` key codes) work automatically; for any other remote, use **Learn my mic button** on the setup screen. Fire TV's Alexa button is handled by Amazon's system and never reaches apps, so Fire TV won't work.
- **The remote's microphone has to switch on for apps.** Google TV devices (onn, Chromecast with Google TV, Google TV Streamer) do this; other boxes should, but haven't been tried.

Two downloads, pick the one for your box's CPU:

| File | For |
|---|---|
| `local-voice-armeabi-v7a.apk` | 32-bit boxes — onn 4K / 4K Plus and many budget sticks |
| `local-voice-arm64-v8a.apk` | 64-bit boxes — e.g. NVIDIA Shield |

Not sure? Try `arm64-v8a` first; if it says the app isn't compatible, use `armeabi-v7a`.

Each download is **Local Voice Setup**, a small installer with Local Voice inside — see [Why is there a setup app?](#why-is-there-a-setup-app) The bare app (`local-voice-app-<abi>.apk`) is also on the release page, for adb installs.

English only for now (other languages are possible with other sherpa-onnx models).

## Install

### Option A — on the TV (no computer)

1. Install **Downloader** (by AFTVnews) from the Play Store.
2. Allow it to install apps: `Settings > Apps > Security & restrictions > Unknown sources > Downloader`.
3. In Downloader, enter the code or link for your box, install it, and choose **Open** (these always point to the newest version):
   - 32-bit (onn 4K / 4K Plus, most budget boxes): code **2007902**, or
     `github.com/privatevoice/local-voice/releases/latest/download/local-voice-armeabi-v7a.apk`
   - 64-bit (e.g. NVIDIA Shield):
     `github.com/privatevoice/local-voice/releases/latest/download/local-voice-arm64-v8a.apk`
4. That opens **Local Voice Setup** (if you see a mic checklist instead, you installed the bare app — see [Troubleshooting](#troubleshooting)). Follow it:
   1. **Allow installing** — turn on *Local Voice Setup* in the screen that opens, then go back.
   2. **Install Local Voice** and confirm. (Already installed Local Voice straight from Downloader before? It offers **Remove old copy** first.)
   3. **Open Local Voice.** Downloader may offer to delete the downloaded file; that's fine. You can also uninstall Local Voice Setup now.
5. In **Local Voice**, follow the checklist:
   1. **Allow microphone.**
   2. **Open accessibility settings**, scroll **all the way to the bottom** of the list, select *Local Voice* and turn it on.
   3. The speech model loads the first time you press the mic button.
   4. Only if pressing the mic button does nothing: choose **Learn my mic button** and press it.
6. Select the **Try it** box, press the remote's mic button, and speak.

**Updating:** enter the same code or link in Downloader to get the newest Setup, open it and press **Reinstall / update**. Your settings are kept. Don't install a new version by any other route, or Android blocks the accessibility service again.

### Option B — with adb (one command)

With [adb](https://developer.android.com/tools/adb) connected to the TV:

```sh
./scripts/setup.sh local-voice-app-armeabi-v7a.apk
```

This installs the bare app (`local-voice-app-<abi>.apk` from the release page), allows the mic, turns on the accessibility service, and sets Local Voice as the system speech recognizer.

## Why is there a setup app?

Since Android 13, Android puts **"restricted settings"** on any app that's installed by opening a downloaded APK file — which is exactly what Downloader does. A restricted app can't turn on an accessibility service, and Local Voice needs one: it's how the app hears the remote's mic button and types into the search box.

On a phone you'd see a "Restricted setting" message and could lift it under the app's ⋮ menu. **Android TV has neither.** The accessibility switch turns on, Android asks "Use Local Voice?", and then the switch quietly flips back off. There's no message and no setting to fix it without a computer.

Apps installed *by another app* through Android's installer API — the way app stores install apps — don't get restricted. So Local Voice Setup carries the Local Voice APK and installs it that way. That's all it does:

- Its only permissions are installing and removing apps. Like Local Voice, it has **no internet permission**; the Local Voice inside it is the same file as `local-voice-app-<abi>.apk` on the release page.
- It doesn't change any Android security setting. Local Voice still asks for the microphone and you still turn on its accessibility service yourself.
- You can uninstall it once Local Voice is installed. Its source is in [`installer/`](installer/).

## Troubleshooting

- **The accessibility switch turns on, then flips back off by itself.** This copy of Local Voice was installed straight from a download (see [above](#why-is-there-a-setup-app)). Install **Local Voice Setup** with Downloader, open it and choose **Remove old copy**, then **Install Local Voice**.
- **Downloader opened Local Voice, not Local Voice Setup.** That's a download from before v0.1.3. Delete it in Downloader's **Files** tab and enter the code or link again.
- **I can't find Local Voice in accessibility settings.** Scroll all the way to the bottom of the list.
- **Pressing the mic button does nothing.** In Local Voice, choose **Learn my mic button** and press it. If no button is detected, your remote's button doesn't reach apps (see [Compatibility](#compatibility)).

## Optional: turn off Google's voice search

Local Voice doesn't need this, but if you want Google's voice service gone entirely:

```sh
adb shell pm disable-user --user 0 com.google.android.katniss
```

Undo with `adb shell pm enable com.google.android.katniss`.

## Known limitations

- **In-app mic buttons** (e.g. the microphone icon inside Nuvio's search screen) currently fail with "microphone permission required" on Android 14 because of how Android checks permissions for third-party recognizers. **Use the remote's mic button instead** — it works everywhere.
- While loaded (from a button press until 3 idle minutes later) the model uses about **230 MB of RAM**.
- A few apps ignore the automatic "Enter" after typing; in those, press Enter/Search on the keyboard yourself.
- Accuracy is good for titles and short phrases, not perfect — it's a small model running on a TV box (e.g. "Dune" can come out as "June").

## How it works

1. The accessibility service intercepts the remote's mic key (`KEYCODE_SEARCH`, `KEYCODE_VOICE_ASSIST`, `KEYCODE_ASSIST`, or the key learned on the setup screen). As a fallback the app also answers Android TV's `android.speech.action.WEB_SEARCH` intent.
2. It starts recording at once with the `VOICE_RECOGNITION` audio source — that's what tells Android TV to switch on the remote's microphone — and loads the speech model in parallel.
3. A small [Silero VAD](https://github.com/snakers4/silero-vad) model detects when you stop talking; the utterance is then transcribed by Moonshine.
4. The text is placed in the focused text field (or the first text field on screen) with an accessibility `SET_TEXT` action, followed by an IME "Enter" action.

The accessibility service only acts when you press the mic button. It doesn't log or store anything, and the app has no network access to send anything with.

## Build from source

Requires JDK 17 and the Android SDK (platform 34).

```sh
./scripts/fetch-deps.sh          # downloads sherpa-onnx (AAR), Moonshine base English and Silero VAD
./gradlew assembleRelease        # app: app/build/outputs/apk/release/, Setup: installer/build/outputs/apk/
```

Release builds are signed with the key described by the file at `$LOCAL_VOICE_SIGNING` (properties: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without one they fall back to the debug key.

## Credits & license

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Apache-2.0
- [Moonshine](https://github.com/moonshine-ai/moonshine) base English model (via sherpa-onnx) — MIT
- [Silero VAD](https://github.com/snakers4/silero-vad) — MIT
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — MIT

Local Voice is licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE).
