#!/bin/bash
# Qwen-TTS Web-Interface starten

set -euo pipefail
cd "$(dirname "$0")/tools/tts"
# exec: Ctrl-C trifft direkt den Python-Prozess, nicht eine hängende Shell.
exec ~/qwen-tts-test/.venv/bin/python ./tts web "$@"
