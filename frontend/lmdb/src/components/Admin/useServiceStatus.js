import { useEffect, useState } from 'react';

/**
 * Polls a URL once and reports whether anything answered.
 *
 * Cross-origin admin tools (Eureka, Kibana) don't send CORS headers for
 * this app's origin, so a normal `fetch()` would reject with a CORS error
 * even when the service is perfectly healthy. `mode: 'no-cors'` sidesteps
 * that: the response is opaque (status/body unreadable), but the promise
 * still resolves on any successful connection and rejects on network
 * failure — enough to answer "is something listening here at all".
 *
 * @param {string} url absolute URL to probe; skipped if falsy
 * @returns {'checking'|'up'|'down'|'unknown'} current probe state
 */
const useServiceStatus = (url) => {
  const [status, setStatus] = useState('checking');

  useEffect(() => {
    if (!url) {
      setStatus('unknown');
      return undefined;
    }

    let cancelled = false;
    setStatus('checking');

    fetch(url, { mode: 'no-cors', cache: 'no-store' })
      .then(() => {
        if (!cancelled) setStatus('up');
      })
      .catch(() => {
        if (!cancelled) setStatus('down');
      });

    return () => {
      cancelled = true;
    };
  }, [url]);

  return status;
};

export default useServiceStatus;
