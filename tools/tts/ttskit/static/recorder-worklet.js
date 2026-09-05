// Sammelt Float32-PCM des ersten Kanals und schickt jeden Block an den
// Hauptthread. Kein MediaRecorder: der liefert Opus/WebM, das der Server nur
// mit ffmpeg lesen könnte — WAV kann er direkt.
class RecorderProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    if (channel) this.port.postMessage(channel.slice());
    return true;
  }
}
registerProcessor("recorder", RecorderProcessor);
