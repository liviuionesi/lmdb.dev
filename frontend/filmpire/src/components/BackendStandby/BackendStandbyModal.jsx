import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  Button,
  Chip,
  LinearProgress,
  IconButton,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';

import { useBackendWakeup } from './useBackendWakeup';

/**
 * Backend Standby & Auto-Wakeup Modal matching Filmpire's exact MUI Dialog design system.
 *
 * <p>Notifies visitors when the backend is in standby mode, displays the ~90s countdown,
 * step progress, and automatically dismisses as soon as the backend is online/ready.
 *
 * @param {Object} props
 * @param {Function} [props.onBackendReady] - Optional callback triggered when backend goes live
 */
function BackendStandbyModal({ onBackendReady }) {
  const [open, setOpen] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  const {
    status,
    secondsRemaining,
    progressPercentage,
    targetCloud,
    currentStep,
    wakeUp,
  } = useBackendWakeup({
    autoWakeup: true,
    onReady: () => {
      setOpen(false);
      if (onBackendReady) {
        onBackendReady();
      }
    },
  });

  useEffect(() => {
    if ((status === 'STANDBY' || status === 'WAKING_UP') && !dismissed) {
      setOpen(true);
    } else if (status === 'ONLINE' || status === 'READY') {
      setOpen(false);
    }
  }, [status, dismissed]);

  const handleClose = () => {
    setDismissed(true);
    setOpen(false);
  };

  const handleSwitchCloud = (cloud) => {
    wakeUp(cloud);
  };

  if (!open) {
    return null;
  }

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pb: 1 }}>
        <Typography variant="h6" fontWeight={700}>
          Backend standby
        </Typography>
        <IconButton
          aria-label="close"
          onClick={handleClose}
          size="small"
          sx={{ color: 'text.secondary' }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
        {/* Status and Countdown Header Box */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            bgcolor: 'action.hover',
            p: 1.5,
            borderRadius: 2,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Chip
              size="small"
              label="Waking up"
              color="primary"
              variant="outlined"
              sx={{ fontWeight: 700, height: 22, fontSize: '0.75rem' }}
            />
            <Typography variant="body2" color="text.secondary">
              {targetCloud === 'azure' ? 'Azure AKS' : 'AWS EC2 (k3s)'}
            </Typography>
          </Box>
          <Typography variant="subtitle2" fontWeight={700} color="primary">
            {`${secondsRemaining}s remaining`}
          </Typography>
        </Box>

        {/* Progress Bar */}
        <LinearProgress
          variant="determinate"
          value={progressPercentage}
          color="primary"
          sx={{ height: 6, borderRadius: 3 }}
        />

        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
          To save energy and cloud costs, the backend enters standby after 1 hour of inactivity. Booting up now...
        </Typography>

        {/* Cloud Switcher Chips */}
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Chip
            label="Azure AKS"
            color={targetCloud === 'azure' ? 'primary' : 'default'}
            variant={targetCloud === 'azure' ? 'filled' : 'outlined'}
            onClick={() => handleSwitchCloud('azure')}
            clickable
            size="small"
            sx={{ flex: 1, fontWeight: 600 }}
          />
          <Chip
            label="AWS EC2 (k3s)"
            color={targetCloud === 'aws' ? 'primary' : 'default'}
            variant={targetCloud === 'aws' ? 'filled' : 'outlined'}
            onClick={() => handleSwitchCloud('aws')}
            clickable
            size="small"
            sx={{ flex: 1, fontWeight: 600 }}
          />
        </Box>

        {/* Step Indicators */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, pt: 0.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                bgcolor: currentStep >= 1 ? 'primary.main' : 'text.disabled',
                flexShrink: 0,
              }}
            />
            <Typography
              variant="caption"
              color={currentStep === 1 ? 'text.primary' : 'text.secondary'}
              fontWeight={currentStep === 1 ? 700 : 400}
            >
              1. Initializing compute nodes ({targetCloud.toUpperCase()})
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                bgcolor: currentStep >= 2 ? 'primary.main' : 'text.disabled',
                flexShrink: 0,
              }}
            />
            <Typography
              variant="caption"
              color={currentStep === 2 ? 'text.primary' : 'text.secondary'}
              fontWeight={currentStep === 2 ? 700 : 400}
            >
              2. Rolling out API Gateway & microservices
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                bgcolor: currentStep >= 3 ? 'success.main' : 'text.disabled',
                flexShrink: 0,
              }}
            />
            <Typography
              variant="caption"
              color={currentStep >= 3 ? 'text.primary' : 'text.secondary'}
              fontWeight={currentStep >= 3 ? 700 : 400}
            >
              3. Warming up MongoDB, Redis & TMDB cache
            </Typography>
          </Box>
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={handleClose} color="inherit" size="small">
          Browse offline
        </Button>
        <Button
          onClick={() => wakeUp(targetCloud)}
          variant="contained"
          color="primary"
          size="small"
        >
          Retry wake-up
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default BackendStandbyModal;
