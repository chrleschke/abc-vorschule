#!/bin/bash
# Qwen-TTS Web-Interface starten

cd "$(dirname "$0")/tools/tts"
~/qwen-tts-test/.venv/bin/python ./tts web "$@"
