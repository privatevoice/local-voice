#!/bin/sh
# One-command setup over adb. Installs Local Voice and turns on everything it needs.
#
#   ./scripts/setup.sh [path/to/local-voice.apk]
#
# With no APK path it only configures an already-installed app.
# Requires adb connected to the TV (Developer options > USB or Wireless debugging).
set -e
PKG=dev.homenetwork.localvoice
A11Y=$PKG/.TextInjectionService
RECOGNIZER=$PKG/.LocalRecognitionService

adb get-state >/dev/null 2>&1 || { echo "No device: connect adb to the TV first."; exit 1; }

if [ -n "$1" ]; then
  echo "Installing $1"
  adb install -r "$1"
fi

echo "Allowing microphone"
adb shell pm grant $PKG android.permission.RECORD_AUDIO

echo "Turning on accessibility (mic button + typing)"
current=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$current" in
  *"$A11Y"*) ;;
  ""|null) adb shell settings put secure enabled_accessibility_services "$A11Y" ;;
  *) adb shell settings put secure enabled_accessibility_services "$current:$A11Y" ;;
esac
adb shell settings put secure accessibility_enabled 1

echo "Setting Local Voice as the system speech recognizer"
adb shell settings put secure voice_recognition_service $RECOGNIZER

echo "Done. Open Local Voice from the app list to check status and try it."
