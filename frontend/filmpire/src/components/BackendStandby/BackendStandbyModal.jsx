import React, { useState, useEffect } from 'react';
import {
  Dialog,
  Box,
  Typography,
  CircularProgress,
  Button,
  Chip,
  LinearProgress,
  IconButton,
} from '@mui/material';
import CloudQueueIcon from '@mui/icons-material/CloudQueue';
import RocketLaunchIcon from '@mui/icons-material/RocketLaunch';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CloseIcon from '@mui/icons-material/Close';
import DnsIcon from '@mui/icons-material/Dns';

import { useBackendWakeup } from './useBackendWakeup';
import useStyles from './styles';

/**
 * Returns class string for a step item based on step progress.
 *
 * @param {number} stepIndex - The step's index (1, 2, or 3)
 * @param {number} currentStep - The currently active step
 * @param {Object} classes - Style classes
 * @returns {string} Combined class names
 */
function getStepItemClass(stepIndex, currentStep, classes) {
  if (currentStep > stepIndex) {
    return `${classes.stepItem} ${classes.stepCompleted}`;
  }
  if (currentStep === stepIndex) {
    return `${classes.stepItem} ${classes.stepActive}`;
  }
  return classes.stepItem;
}

/**
 * Modern Glassmorphic Backend Standby & Auto-Wakeup Modal.
 *
 * <p>Greets visitors when the cloud cluster is in Eco-Sleep mode, displays a dynamic ~90s
 * countdown with multi-step progression, polls backend health, and auto-dismisses once the
 * backend responds 200 OK.
 *
 * @param {Object} props
 * @param {Function} [props.onBackendReady] - Optional callback triggered when backend goes live
 */
function BackendStandbyModal({ onBackendReady }) {
  const { classes } = useStyles();
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
      if (onBackendReady) {
        onBackendReady();
      }
      // Auto-dismiss after 1.5s celebration
      setTimeout(() => {
        setOpen(false);
      }, 1500);
    },
  });

  useEffect(() => {
    if ((status === 'STANDBY' || status === 'WAKING_UP' || status === 'READY') && !dismissed) {
      setOpen(true);
    } else if (status === 'ONLINE') {
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

  const isReady = status === 'READY';

  if (!open) {
    return null;
  }

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      slotProps={{
        backdrop: { className: classes.modalBackdrop },
      }}
      PaperProps={{
        className: classes.glassCard,
        elevation: 0,
      }}
    >
      <Box position="relative">
        <IconButton
          aria-label="close"
          onClick={handleClose}
          sx={{
            position: 'absolute',
            right: -10,
            top: -10,
            color: 'text.secondary',
          }}
          size="small"
        >
          <CloseIcon fontSize="small" />
        </IconButton>

        {/* Header Icon / Progress Ring */}
        <Box className={classes.pulseCircle}>
          {isReady ? (
            <CheckCircleIcon sx={{ fontSize: 72, color: '#10b981' }} />
          ) : (
            <Box position="relative" display="inline-flex">
              <CircularProgress
                variant="determinate"
                value={100}
                size={84}
                thickness={3.5}
                sx={{ color: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)') }}
              />
              <CircularProgress
                variant="determinate"
                value={progressPercentage}
                size={84}
                thickness={3.5}
                sx={{
                  color: '#3b82f6',
                  position: 'absolute',
                  left: 0,
                  strokeLinecap: 'round',
                  transition: 'all 0.5s ease',
                }}
              />
              <Box
                sx={{
                  top: 0,
                  left: 0,
                  bottom: 0,
                  right: 0,
                  position: 'absolute',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexDirection: 'column',
                }}
              >
                <Typography variant="h6" component="div" fontWeight={800} color="primary">
                  {secondsRemaining}s
                </Typography>
              </Box>
            </Box>
          )}
        </Box>

        {/* Title and Tagline */}
        <Typography variant="h5" fontWeight={800} gutterBottom>
          {isReady ? 'Backend Online!' : 'Waking Up Cloud Cluster'}
        </Typography>

        <Typography variant="body2" color="text.secondary" sx={{ mb: 2, px: 2 }}>
          {isReady
            ? 'Services are fully initialized. Enjoy streaming on Filmpire!'
            : 'To save energy & cloud costs, the backend auto-sleeps after 1 hour of idle time. It is now spinning up on-demand.'}
        </Typography>

        {/* Cloud Switcher Buttons */}
        {!isReady && (
          <Box display="flex" justifyContent="center" gap={1} mb={2}>
            <Chip
              icon={<CloudQueueIcon />}
              label="Azure AKS"
              color={targetCloud === 'azure' ? 'primary' : 'default'}
              variant={targetCloud === 'azure' ? 'filled' : 'outlined'}
              onClick={() => handleSwitchCloud('azure')}
              clickable
              sx={{ fontWeight: 600 }}
            />
            <Chip
              icon={<DnsIcon />}
              label="AWS EC2 (k3s)"
              color={targetCloud === 'aws' ? 'primary' : 'default'}
              variant={targetCloud === 'aws' ? 'filled' : 'outlined'}
              onClick={() => handleSwitchCloud('aws')}
              clickable
              sx={{ fontWeight: 600 }}
            />
          </Box>
        )}

        {/* Linear Progress Bar */}
        {!isReady && (
          <LinearProgress
            variant="determinate"
            value={progressPercentage}
            sx={{
              height: 6,
              borderRadius: 3,
              mb: 2,
              bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'),
            }}
          />
        )}

        {/* Step Progress Tracker */}
        {!isReady && (
          <Box className={classes.stepContainer}>
            <Box className={getStepItemClass(1, currentStep, classes)}>
              <RocketLaunchIcon sx={{ fontSize: 18, color: currentStep >= 1 ? '#3b82f6' : 'text.disabled' }} />
              <Typography variant="caption" fontWeight={currentStep === 1 ? 700 : 500}>
                1. Initializing {targetCloud.toUpperCase()} compute nodes
              </Typography>
            </Box>

            <Box className={getStepItemClass(2, currentStep, classes)}>
              <CloudQueueIcon sx={{ fontSize: 18, color: currentStep >= 2 ? '#3b82f6' : 'text.disabled' }} />
              <Typography variant="caption" fontWeight={currentStep === 2 ? 700 : 500}>
                2. Booting Gateway, Movies & AI microservices
              </Typography>
            </Box>

            <Box className={getStepItemClass(3, currentStep, classes)}>
              <CheckCircleIcon sx={{ fontSize: 18, color: currentStep >= 3 ? '#10b981' : 'text.disabled' }} />
              <Typography variant="caption" fontWeight={currentStep >= 3 ? 700 : 500}>
                3. Warming up MongoDB, Redis & TMDB cache
              </Typography>
            </Box>
          </Box>
        )}

        {/* Bottom Actions */}
        <Box mt={3} display="flex" justifyContent="center" gap={1.5}>
          <Button
            variant="text"
            size="small"
            onClick={handleClose}
            sx={{ textTransform: 'none', color: 'text.secondary', fontWeight: 600 }}
          >
            Browse Offline Mode
          </Button>
          {!isReady && (
            <Button
              variant="outlined"
              size="small"
              onClick={() => wakeUp(targetCloud)}
              sx={{ textTransform: 'none', fontWeight: 600 }}
            >
              Retry Wake-Up
            </Button>
          )}
        </Box>
      </Box>
    </Dialog>
  );
}

export default BackendStandbyModal;
