import React, { useEffect, useState, useCallback } from 'react';
import {
  Box,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Tooltip,
} from '@mui/material';
import CloudIcon from '@mui/icons-material/Cloud';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import BedtimeIcon from '@mui/icons-material/Bedtime';
import SettingsEthernetIcon from '@mui/icons-material/SettingsEthernet';
import useStyles from './styles';
import {
  resolveApiUrl,
  getBackendTarget,
  setBackendTarget,
  invalidateResolutionCache,
} from '../../utils/apiUrl';

/**
 * Formats a duration in seconds into a clean human-readable string (e.g. "2h 15m", "45s").
 *
 * @param {number} totalSeconds
 * @returns {string}
 */
export function formatDuration(totalSeconds) {
  if (totalSeconds == null || totalSeconds < 0) return '0s';
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const parts = [];
  if (days > 0) parts.push(`${days}d`);
  if (hours > 0) parts.push(`${hours}h`);
  if (minutes > 0 || (days === 0 && hours === 0 && seconds === 0)) parts.push(`${minutes}m`);
  if (days === 0 && hours === 0 && seconds > 0) parts.push(`${seconds}s`);

  return parts.slice(0, 2).join(' ');
}

function Footer() {
  const { classes } = useStyles();
  const [telemetry, setTelemetry] = useState(null);
  const [status, setStatus] = useState('checking'); // 'up' | 'standby' | 'down' | 'checking'
  const [currentTarget, setCurrentTarget] = useState(getBackendTarget());
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedTarget, setSelectedTarget] = useState(getBackendTarget());
  const [activeUrl, setActiveUrl] = useState('');

  const fetchTelemetry = useCallback(async () => {
    try {
      const baseUrl = await resolveApiUrl();
      if (!baseUrl) {
        setStatus('standby');
        setTelemetry(null);
        return;
      }
      setActiveUrl(baseUrl);
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 3000);
      const res = await fetch(`${baseUrl}/actuator/activity`, {
        signal: controller.signal,
        cache: 'no-store',
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        setTelemetry(data);
        setStatus('up');
      } else {
        setStatus('standby');
      }
    } catch {
      setStatus('standby');
    }
  }, []);

  useEffect(() => {
    fetchTelemetry();
    const interval = setInterval(fetchTelemetry, 25000);
    return () => clearInterval(interval);
  }, [fetchTelemetry]);

  const handleOpenDialog = () => {
    setSelectedTarget(getBackendTarget());
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
  };

  const handleSaveTarget = () => {
    setBackendTarget(selectedTarget);
    setCurrentTarget(selectedTarget);
    invalidateResolutionCache();
    setDialogOpen(false);
    setStatus('checking');
    setTimeout(fetchTelemetry, 500);
  };

  // Resolve user-friendly provider display
  const providerLabel = telemetry?.cloudProviderLabel
    || (currentTarget === 'aws' ? 'Amazon Web Services (k3s)'
      : currentTarget === 'minikube' ? 'Local Minikube Cluster'
        : 'Microsoft Azure (AKS)');

  const dotClass = status === 'up'
    ? classes.onlineDot
    : status === 'standby'
      ? classes.standbyDot
      : classes.offlineDot;

  return (
    <footer className={classes.footerContainer}>
      <Tooltip title="Click to view backend telemetry or switch target provider" arrow>
        <Box
          className={classes.statusBadge}
          onClick={handleOpenDialog}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') handleOpenDialog();
          }}
          aria-label="Backend status and cloud provider info"
        >
          <span className={`${classes.pulseDot} ${dotClass}`} />
          <Typography component="span" className={classes.providerText}>
            Powered by {providerLabel}
          </Typography>

          {status === 'up' && telemetry && (
            <>
              <span className={classes.divider} />
              <Box className={classes.metaItem}>
                <AccessTimeIcon sx={{ fontSize: 14 }} />
                <Typography component="span" variant="caption">
                  Uptime: {formatDuration(telemetry.uptimeSeconds)}
                </Typography>
              </Box>

              <span className={classes.divider} />
              <Box className={classes.metaItem}>
                <BedtimeIcon sx={{ fontSize: 14 }} />
                <Typography component="span" variant="caption">
                  Auto-sleep: {formatDuration(telemetry.secondsUntilAutoStop)}
                </Typography>
              </Box>
            </>
          )}

          {status === 'standby' && (
            <>
              <span className={classes.divider} />
              <Typography component="span" variant="caption" sx={{ color: 'warning.main' }}>
                Standby (Auto-Wake Ready)
              </Typography>
            </>
          )}
        </Box>
      </Tooltip>

      <Typography className={classes.copyrightText}>
        © {new Date().getFullYear()} Filmpire Microservices • Multi-Cloud Resilient Architecture
      </Typography>

      <Dialog open={dialogOpen} onClose={handleCloseDialog} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <CloudIcon color="primary" /> Backend Cloud Provider
        </DialogTitle>
        <DialogContent className={classes.dialogContent}>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            Select which cloud environment the frontend connects to:
          </Typography>

          <FormControl fullWidth size="small" className={classes.targetSelect}>
            <InputLabel id="cloud-provider-select-label">Active Target</InputLabel>
            <Select
              labelId="cloud-provider-select-label"
              value={selectedTarget}
              label="Active Target"
              onChange={(e) => setSelectedTarget(e.target.value)}
            >
              <MenuItem value="azure">Microsoft Azure (AKS)</MenuItem>
              <MenuItem value="aws">Amazon Web Services (k3s)</MenuItem>
              <MenuItem value="minikube">Local Minikube Cluster</MenuItem>
            </Select>
          </FormControl>

          {telemetry && (
            <Box sx={{ mt: 2, p: 1.5, bgcolor: 'action.hover', borderRadius: 1 }}>
              <Typography variant="caption" display="block">
                <strong>Active URL:</strong> {activeUrl || 'Auto-resolved'}
              </Typography>
              <Typography variant="caption" display="block">
                <strong>Status:</strong>{' '}
                <Chip
                  label={telemetry.status || 'UP'}
                  size="small"
                  color="success"
                  sx={{ height: 18, fontSize: '0.65rem' }}
                />
              </Typography>
              <Typography variant="caption" display="block">
                <strong>Uptime:</strong> {formatDuration(telemetry.uptimeSeconds)}
              </Typography>
              <Typography variant="caption" display="block">
                <strong>Idle Time:</strong> {formatDuration(telemetry.idleSeconds)}
              </Typography>
              <Typography variant="caption" display="block">
                <strong>Time to Sleep:</strong> {formatDuration(telemetry.secondsUntilAutoStop)} (Threshold: {formatDuration(telemetry.idleThresholdSeconds)})
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button onClick={handleSaveTarget} variant="contained" color="primary">
            Apply Target
          </Button>
        </DialogActions>
      </Dialog>
    </footer>
  );
}

export default Footer;
