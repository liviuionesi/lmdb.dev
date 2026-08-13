import React, { useEffect, useState } from 'react';
import { Box, Typography } from '@mui/material';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import BedtimeIcon from '@mui/icons-material/Bedtime';
import useStyles from './styles';
import { resolveApiUrl } from '../../utils/apiUrl';

function Footer() {
  const { classes } = useStyles();
  const [providerLabel, setProviderLabel] = useState('Microsoft Azure (AKS)');
  const [status, setStatus] = useState('up'); // 'up' | 'standby'

  useEffect(() => {
    // Resolve once on mount without periodic polling
    let isMounted = true;
    (async () => {
      try {
        const baseUrl = await resolveApiUrl();
        if (!baseUrl) {
          if (isMounted) setStatus('standby');
          return;
        }
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 2500);
        const res = await fetch(`${baseUrl}/actuator/activity`, {
          signal: controller.signal,
          cache: 'no-store',
        });
        clearTimeout(timeoutId);

        if (res.ok && isMounted) {
          const data = await res.json();
          if (data.cloudProviderLabel) {
            setProviderLabel(data.cloudProviderLabel);
          }
          setStatus('up');
        } else if (isMounted) {
          setStatus('standby');
        }
      } catch {
        if (isMounted) setStatus('standby');
      }
    })();

    return () => {
      isMounted = false;
    };
  }, []);

  const dotClass = status === 'up' ? classes.onlineDot : classes.standbyDot;

  return (
    <footer className={classes.footerContainer}>
      <Box className={classes.statusBadge} aria-label="Backend status badge">
        <span className={`${classes.pulseDot} ${dotClass}`} />
        <Typography component="span" className={classes.providerText}>
          Powered by {providerLabel}
        </Typography>

        <span className={classes.divider} />
        <Box className={classes.metaItem}>
          <AccessTimeIcon sx={{ fontSize: 13 }} />
          <Typography component="span" variant="caption">
            Uptime: On-Demand
          </Typography>
        </Box>

        <span className={classes.divider} />
        <Box className={classes.metaItem}>
          <BedtimeIcon sx={{ fontSize: 13 }} />
          <Typography component="span" variant="caption">
            Auto-sleep: 1h idle
          </Typography>
        </Box>
      </Box>

      <Typography className={classes.copyrightText}>
        © {new Date().getFullYear()} Filmpire Microservices • Multi-Cloud Architecture
      </Typography>
    </footer>
  );
}

export default Footer;
