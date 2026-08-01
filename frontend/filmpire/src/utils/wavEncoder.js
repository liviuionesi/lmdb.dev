const TARGET_SAMPLE_RATE = 16000;

//* Downmixes to mono and linearly resamples to 16kHz — the format
//* ai-service's Vosk model expects (#68). Linear interpolation is good
//* enough for speech-command recognition; no extra library needed.
const resampleToMono16k = (audioBuffer) => {
  const { numberOfChannels, sampleRate, length } = audioBuffer;

  const mono = new Float32Array(length);
  for (let channel = 0; channel < numberOfChannels; channel += 1) {
    const data = audioBuffer.getChannelData(channel);
    for (let i = 0; i < length; i += 1) {
      mono[i] += data[i] / numberOfChannels;
    }
  }

  if (sampleRate === TARGET_SAMPLE_RATE) {
    return mono;
  }

  const ratio = sampleRate / TARGET_SAMPLE_RATE;
  const outLength = Math.max(1, Math.round(length / ratio));
  const resampled = new Float32Array(outLength);
  for (let i = 0; i < outLength; i += 1) {
    const srcIndex = i * ratio;
    const lower = Math.floor(srcIndex);
    const upper = Math.min(lower + 1, length - 1);
    const weight = srcIndex - lower;
    resampled[i] = (mono[lower] * (1 - weight)) + (mono[upper] * weight);
  }
  return resampled;
};

const floatTo16BitPcm = (samples) => {
  const buffer = new ArrayBuffer(samples.length * 2);
  const view = new DataView(buffer);
  for (let i = 0; i < samples.length; i += 1) {
    const clamped = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(i * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true);
  }
  return buffer;
};

const writeString = (view, offset, str) => {
  for (let i = 0; i < str.length; i += 1) {
    view.setUint8(offset + i, str.charCodeAt(i));
  }
};

const buildWavHeader = (pcmByteLength) => {
  const buffer = new ArrayBuffer(44);
  const view = new DataView(buffer);

  writeString(view, 0, 'RIFF');
  view.setUint32(4, 36 + pcmByteLength, true);
  writeString(view, 8, 'WAVE');
  writeString(view, 12, 'fmt ');
  view.setUint32(16, 16, true); // PCM chunk size
  view.setUint16(20, 1, true); // PCM format
  view.setUint16(22, 1, true); // mono
  view.setUint32(24, TARGET_SAMPLE_RATE, true);
  view.setUint32(28, TARGET_SAMPLE_RATE * 2, true); // byte rate (mono * 16-bit)
  view.setUint16(32, 2, true); // block align
  view.setUint16(34, 16, true); // bits per sample
  writeString(view, 36, 'data');
  view.setUint32(40, pcmByteLength, true);

  return buffer;
};

//* Decodes whatever MediaRecorder produced (webm/opus in Chrome) and
//* re-encodes it as a mono 16kHz PCM16 WAV blob — the exact format
//* ai-service's POST /api/v1/ai/speech-to-text endpoint requires.
export const encodeToWav = async (audioBlob) => {
  const arrayBuffer = await audioBlob.arrayBuffer();
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  const audioContext = new AudioContextClass();

  try {
    const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
    const pcmSamples = resampleToMono16k(audioBuffer);
    const pcmBuffer = floatTo16BitPcm(pcmSamples);
    const headerBuffer = buildWavHeader(pcmBuffer.byteLength);

    return new Blob([headerBuffer, pcmBuffer], { type: 'audio/wav' });
  } finally {
    await audioContext.close();
  }
};
