#!/bin/sh
# Downloads the sherpa-onnx Android library and the streaming Zipformer English model
# (both Apache-2.0) into the places the build expects. Run once after cloning.
set -e
cd "$(dirname "$0")/.."
V=1.13.8
mkdir -p app/libs app/src/main/assets/model
curl -fL -o app/libs/sherpa-onnx-$V.aar \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v$V/sherpa-onnx-$V.aar
M=sherpa-onnx-streaming-zipformer-en-2023-06-26
T=$(mktemp -d)
curl -fL -o "$T/m.tar.bz2" \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$M.tar.bz2
tar -xjf "$T/m.tar.bz2" -C "$T"
cp "$T/$M"/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx \
   "$T/$M"/decoder-epoch-99-avg-1-chunk-16-left-128.onnx \
   "$T/$M"/joiner-epoch-99-avg-1-chunk-16-left-128.onnx \
   "$T/$M"/tokens.txt app/src/main/assets/model/
rm -rf "$T"
echo "deps ready"
