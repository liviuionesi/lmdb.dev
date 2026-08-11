import { useState, useRef, useEffect, useCallback } from 'react';

/**
 * Web Audio API synthesizer hook creating authentic vintage 35mm projector hum,
 * rhythmic sprocket clicks, and cinema audio feedback with zero external assets.
 *
 * @returns {Object} Sound toggle state and control actions
 */
export function useCinemaAudio() {
  const [isPlaying, setIsPlaying] = useState(false);
  const audioCtxRef = useRef(null);
  const humNodeRef = useRef(null);
  const clickIntervalRef = useRef(null);

  const stopAudio = useCallback(() => {
    if (clickIntervalRef.current) {
      clearInterval(clickIntervalRef.current);
      clickIntervalRef.current = null;
    }
    if (humNodeRef.current) {
      try {
        humNodeRef.current.stop();
        humNodeRef.current.disconnect();
      } catch {
        // Ignored
      }
      humNodeRef.current = null;
    }
    if (audioCtxRef.current && audioCtxRef.current.state !== 'closed') {
      try {
        audioCtxRef.current.close();
      } catch {
        // Ignored
      }
      audioCtxRef.current = null;
    }
    setIsPlaying(false);
  }, []);

  const playClick = useCallback((ctx) => {
    if (!ctx || ctx.state === 'closed') return;
    try {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(120, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(30, ctx.currentTime + 0.04);

      gain.gain.setValueAtTime(0.04, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.04);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start();
      osc.stop(ctx.currentTime + 0.05);
    } catch {
      // Ignored
    }
  }, []);

  const startAudio = useCallback(() => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;

      const ctx = new AudioCtx();
      audioCtxRef.current = ctx;

      // 1. Warm 45Hz projector transformer hum
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      const filter = ctx.createBiquadFilter();

      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(45, ctx.currentTime);

      filter.type = 'lowpass';
      filter.frequency.setValueAtTime(180, ctx.currentTime);

      gain.gain.setValueAtTime(0.03, ctx.currentTime);

      osc.connect(filter);
      filter.connect(gain);
      gain.connect(ctx.destination);

      osc.start();
      humNodeRef.current = osc;

      // 2. 24fps rhythmic film projector sprocket tick (approx every 180ms)
      clickIntervalRef.current = setInterval(() => {
        playClick(ctx);
      }, 180);

      setIsPlaying(true);
    } catch {
      setIsPlaying(false);
    }
  }, [playClick]);

  const toggleAudio = useCallback(() => {
    if (isPlaying) {
      stopAudio();
    } else {
      startAudio();
    }
  }, [isPlaying, startAudio, stopAudio]);

  useEffect(() => () => {
    stopAudio();
  }, [stopAudio]);

  return {
    isPlaying,
    toggleAudio,
    stopAudio,
  };
}

export default useCinemaAudio;
