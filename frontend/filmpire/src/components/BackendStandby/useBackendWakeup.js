import { useState, useEffect, useCallback, useRef } from 'react';
import {
  resolveApiUrl,
  triggerBackendWakeup,
  invalidateResolutionCache,
  subscribeBackendStatus,
  getBackendTarget,
  setBackendTarget,
} from '../../utils/apiUrl';

const ESTIMATED_WAKEUP_SECONDS = 90;
const POLL_INTERVAL_MS = 4000;

/**
 * React hook that monitors backend availability, manages automated auto-wakeup on page visit,
 * and tracks the countdown and health-polling loop until the cluster is live.
 *
 * @param {Object} [options]
 * @param {boolean} [options.autoWakeup=true] - Whether to automatically trigger wake-up on detection of offline state
 * @param {Function} [options.onReady] - Callback invoked when the backend becomes healthy
 * @returns {Object} Wakeup state and control actions
 */
export function useBackendWakeup({ autoWakeup = true, onReady } = {}) {
  const [status, setStatus] = useState('CHECKING'); // 'CHECKING' | 'ONLINE' | 'STANDBY' | 'WAKING_UP' | 'READY'
  const [secondsRemaining, setSecondsRemaining] = useState(ESTIMATED_WAKEUP_SECONDS);
  const [targetCloud, setTargetCloud] = useState(() => getBackendTarget());
  const [currentStep, setCurrentStep] = useState(1);
  const onReadyRef = useRef(onReady);
  onReadyRef.current = onReady;

  // 1. Initial health probe — resolveApiUrl() returns non-null only when a tier is healthy
  const checkHealth = useCallback(async () => {
    const activeUrl = await resolveApiUrl();
    if (activeUrl) {
      setStatus('ONLINE');
      return true;
    }
    return false;
  }, []);

  // 2. Dispatch wake-up signal
  const wakeUp = useCallback(async (cloud = targetCloud) => {
    setStatus('WAKING_UP');
    setSecondsRemaining(ESTIMATED_WAKEUP_SECONDS);
    setTargetCloud(cloud);
    setBackendTarget(cloud);
    setCurrentStep(1);

    try {
      await triggerBackendWakeup(cloud);
    } catch {
      // Ignored: health polling will still continue
    }
  }, [targetCloud]);

  // Initial check and auto-wakeup on mount
  useEffect(() => {
    let isMounted = true;

    async function initialize() {
      setStatus('CHECKING');
      // resolveApiUrl() walks localhost → cloud → tunnel and returns the first live URL,
      // or null if every tier timed out / refused.
      const activeUrl = await resolveApiUrl();
      if (!isMounted) return;

      if (activeUrl) {
        setStatus('ONLINE');
      } else if (autoWakeup) {
        setStatus('WAKING_UP');
        setSecondsRemaining(ESTIMATED_WAKEUP_SECONDS);
        setCurrentStep(1);
        try {
          await triggerBackendWakeup(targetCloud);
        } catch {
          // Ignored
        }
      } else {
        setStatus('STANDBY');
      }
    }

    initialize();

    const unsubscribe = subscribeBackendStatus((newStatus, details) => {
      if (!isMounted) return;
      if (newStatus === 'WAKING_UP') {
        // Only accept external WAKING_UP if we already left CHECKING
        setStatus((prev) => (prev !== 'CHECKING' ? 'WAKING_UP' : prev));
        if (details?.targetCloud) setTargetCloud(details.targetCloud);
      } else if (newStatus === 'READY') {
        setStatus('READY');
      }
      // Ignore external STANDBY — hook owns that determination via checkBackendHealth
    });

    return () => {
      isMounted = false;
      unsubscribe();
    };
  }, [autoWakeup, targetCloud]);

  // 3. Countdown timer when waking up or in standby
  useEffect(() => {
    if (status !== 'WAKING_UP' && status !== 'STANDBY') return undefined;

    const timer = setInterval(() => {
      setSecondsRemaining((prev) => {
        if (prev <= 1) return 0;
        const next = prev - 1;
        if (next <= 30) {
          setCurrentStep(3); // Warming up services
        } else if (next <= 60) {
          setCurrentStep(2); // Starting pods
        }
        return next;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [status]);

  // 4. Polling loop when waking up or in standby
  useEffect(() => {
    if (status !== 'WAKING_UP' && status !== 'STANDBY') return undefined;

    let isMounted = true;
    const pollTimer = setInterval(async () => {
      const activeUrl = await resolveApiUrl();
      if (!isMounted) return;
      if (activeUrl) {
        invalidateResolutionCache();
        setStatus('READY');
        clearInterval(pollTimer);
        if (onReadyRef.current) {
          onReadyRef.current();
        }
      }
    }, POLL_INTERVAL_MS);

    return () => {
      isMounted = false;
      clearInterval(pollTimer);
    };
  }, [status]);

  return {
    status,
    secondsRemaining,
    totalSeconds: ESTIMATED_WAKEUP_SECONDS,
    progressPercentage: Math.min(
      100,
      Math.round(((ESTIMATED_WAKEUP_SECONDS - secondsRemaining) / ESTIMATED_WAKEUP_SECONDS) * 100),
    ),
    targetCloud,
    currentStep,
    wakeUp,
    checkHealth,
  };
}

export default useBackendWakeup;
