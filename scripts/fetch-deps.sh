#!/bin/sh
# Downloads the sherpa-onnx Android library, the Moonshine base English model and the
# Silero VAD model into the places the build expects. Run once after cloning.
set -e
cd "$(dirname "$0")/.."
V=1.13.8
R=https://github.com/k2-fsa/sherpa-onnx/releases/download
mkdir -p app/libs app/src/main/assets/model
curl -fL -o app/libs/sherpa-onnx-$V.aar $R/v$V/sherpa-onnx-$V.aar
M=sherpa-onnx-moonshine-base-en-quantized-2026-02-27
T=$(mktemp -d)
curl -fL -o "$T/m.tar.bz2" $R/asr-models/$M.tar.bz2
tar -xjf "$T/m.tar.bz2" -C "$T"
cp "$T/$M"/encoder_model.ort "$T/$M"/decoder_model_merged.ort "$T/$M"/tokens.txt app/src/main/assets/model/
curl -fL -o app/src/main/assets/model/silero_vad.onnx $R/asr-models/silero_vad.onnx
rm -rf "$T"
echo "deps ready"
