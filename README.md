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

Tested on an **onn 4K Plus** (Google TV, Android 14). It should work on other Android TV / Google TV devices running Android 7+, but that's untested — reports welcome.

Two downloads, pick the one for your box's CPU:

| File | For |
|---|---|
| `local-voice-armeabi-v7a.apk` | 32-bit boxes — onn 4K / 4K Plus and many budget sticks |
| `local-voice-arm64-v8a.apk` | 64-bit boxes — e.g. NVIDIA Shield |

Not sure? Try `arm64-v8a` first; if Android says the app isn't compatible, use `armeabi-v7a`.

English only for now (other languages are possible with other sherpa-onnx models).

## Install

### Option A — on the TV (no computer)

1. Install **Downloader** (by AFTVnews) from the Play Store.
2. Allow it to install apps: `Settings > Apps > Security & restrictions > Unknown sources > Downloader`.
3. In Downloader, enter the link for your box and install it (these always point to the newest version):
   - 32-bit (onn 4K / 4K Plus, most budget boxes):
     `github.com/privatevoice/local-voice/releases/latest/download/local-voice-armeabi-v7a.apk`
   - 64-bit (e.g. NVIDIA Shield):
     `github.com/privatevoice/local-voice/releases/latest/download/local-voice-arm64-v8a.apk`
4. Open **Local Voice** from your app list and follow the checklist:
   1. **Allow microphone.**
   2. **Open accessibility settings** and turn on *Local Voice*.
      If the switch is greyed out ("restricted setting"), go to `Settings > Apps > Local Voice`, press the **⋮** menu → **Allow restricted settings**, then try again. Android does this for apps installed outside the Play Store.
   3. Wait for **Speech model: ✓ Ready** (about 10 seconds).
5. Select the **Try it** box, press the remote's mic button, and speak.

### Option B — with adb (one command)

With [adb](https://developer.android.com/tools/adb) connected to the TV:

```sh
./scripts/setup.sh local-voice-armeabi-v7a.apk
```

This installs the app, allows the mic, turns on the accessibility service, and sets Local Voice as the system speech recognizer.

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

1. The accessibility service intercepts the remote's mic key (`KEYCODE_SEARCH` / `KEYCODE_VOICE_ASSIST`). As a fallback the app also answers Android TV's `android.speech.action.WEB_SEARCH` intent.
2. It starts recording at once with the `VOICE_RECOGNITION` audio source — that's what tells Android TV to switch on the remote's microphone — and loads the speech model in parallel.
3. A small [Silero VAD](https://github.com/snakers4/silero-vad) model detects when you stop talking; the utterance is then transcribed by Moonshine.
4. The text is placed in the focused text field (or the first text field on screen) with an accessibility `SET_TEXT` action, followed by an IME "Enter" action.

The accessibility service only acts when you press the mic button. It doesn't log or store anything, and the app has no network access to send anything with.

## Build from source

Requires JDK 17 and the Android SDK (platform 34).

```sh
./scripts/fetch-deps.sh          # downloads sherpa-onnx (AAR), Moonshine base English and Silero VAD
./gradlew assembleRelease        # APKs in app/build/outputs/apk/release/
```

Release builds are signed with the key described by the file at `$LOCAL_VOICE_SIGNING` (properties: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without one they fall back to the debug key.

## Credits & license

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Apache-2.0
- [Moonshine](https://github.com/moonshine-ai/moonshine) base English model (via sherpa-onnx) — MIT
- [Silero VAD](https://github.com/snakers4/silero-vad) — MIT
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — MIT

Local Voice is licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE).
