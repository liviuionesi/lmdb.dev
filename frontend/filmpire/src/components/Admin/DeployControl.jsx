import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  ToggleButtonGroup,
  ToggleButton,
  Button,
  Alert,
  CircularProgress,
  Chip,
  Divider,
  Paper,
} from '@mui/material';
import CloudQueueIcon from '@mui/icons-material/CloudQueue';
import CloudDoneIcon from '@mui/icons-material/CloudDone';
import PowerSettingsNewIcon from '@mui/icons-material/PowerSettingsNew';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonCheckedIcon from '@mui/icons-material/RadioButtonChecked';
import StorageIcon from '@mui/icons-material/Storage';
import LaptopMacIcon from '@mui/icons-material/LaptopMac';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import AccessTimeIcon from '@mui/icons-material/AccessTime';

import { getApiUrl } from '../../utils/apiUrl';

const REPO_OWNER = 'pehlivanu';
const REPO_NAME = 'filmpire-microservices';
const ACTIVE_TUNNEL_URL = 'https://humanities-exactly-criterion-buyer.trycloudflare.com';

/**
 * Modern, Executive 1-Click Cloud & Local Deployment Deck.
 * Provides instant 1-click cloud orchestration with zero clutter and zero manual setup.
 *
 * @author Filmpire Development Team
 * @version 2.0.0
 */
function DeployControl({ apiUrl }) {
  const [cloudTarget, setCloudTarget] = useState('azure');
  const [isDeploying, setIsDeploying] = useState(false);
  const [isDestroying, setIsDestroying] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [backendHealthy, setBackendHealthy] = useState(false);
  const [sessionStartTime, setSessionStartTime] = useState(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  const checkBackendHealth = useCallback(async () => {
    const targetUrl = apiUrl || getApiUrl();
    try {
      const res = await fetch(`${targetUrl}/actuator/health`, { method: 'GET', mode: 'cors' });
      if (res.ok) {
        setBackendHealthy(true);
        if (!sessionStartTime) {
          setSessionStartTime(Date.now());
        }
      } else {
        setBackendHealthy(false);
      }
    } catch {
      setBackendHealthy(false);
    }
  }, [apiUrl, sessionStartTime]);

  useEffect(() => {
    checkBackendHealth();
    const interval = setInterval(checkBackendHealth, 10000);
    return () => clearInterval(interval);
  }, [checkBackendHealth]);

  useEffect(() => {
    if (!sessionStartTime || !backendHealthy) {
      return undefined;
    }
    const timer = setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - sessionStartTime) / 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, [sessionStartTime, backendHealthy]);

  const dispatchWorkflow = async (workflowFile, inputs = {}) => {
    setErrorMessage('');
    const token = import.meta.env.VITE_GITHUB_TOKEN || localStorage.getItem('filmpire_gh_token');

    if (!token) {
      setErrorMessage('GitHub Personal Access Token or VITE_GITHUB_TOKEN is required to trigger automated cloud deployment.');
      return false;
    }

    try {
      const response = await fetch(
        `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/actions/workflows/${workflowFile}/dispatches`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: 'application/vnd.github.v3+json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            ref: 'develop',
            inputs,
          }),
        },
      );

      if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.message || `GitHub API error: ${response.status}`);
      }

      return true;
    } catch (err) {
      setErrorMessage(err.message);
      return false;
    }
  };

  const handleDeploy = async () => {
    if (cloudTarget === 'local') {
      localStorage.setItem('filmpire_api_url', ACTIVE_TUNNEL_URL);
      setStatusMessage(`Local Cloudflare Tunnel linked (${ACTIVE_TUNNEL_URL})! Verifying gateway health...`);
      await checkBackendHealth();
      return;
    }

    setIsDeploying(true);
    setStatusMessage(`Provisioning ${cloudTarget.toUpperCase()} cloud backend & linking DNS...`);

    const success = await dispatchWorkflow('deploy.yml', { cloud: cloudTarget });
    if (success) {
      localStorage.setItem('filmpire_api_url', 'https://filmpire-api.duckdns.org');
      setStatusMessage(`Automated deployment dispatched for ${cloudTarget.toUpperCase()}! Cloud backend is spinning up.`);
    }
    setIsDeploying(false);
  };

  const handleDestroy = async () => {
    if (cloudTarget === 'local') {
      localStorage.removeItem('filmpire_api_url');
      setStatusMessage('Local tunnel disconnected. Frontend reset to cloud DNS.');
      setSessionStartTime(null);
      setElapsedSeconds(0);
      setBackendHealthy(false);
      return;
    }

    setIsDestroying(true);
    setStatusMessage(`Terminating ${cloudTarget.toUpperCase()} backend to maintain $0 spend...`);

    const success = await dispatchWorkflow('destroy.yml', { cloud: cloudTarget });
    if (success) {
      setStatusMessage(`Teardown dispatched for ${cloudTarget.toUpperCase()}! Cloud resources will be destroyed.`);
      setSessionStartTime(null);
      setElapsedSeconds(0);
      setBackendHealthy(false);
    }
    setIsDestroying(false);
  };

  const formatElapsed = (sec) => {
    const mins = Math.floor(sec / 60);
    const s = sec % 60;
    return `${mins}m ${s}s`;
  };

  const estimatedCost = cloudTarget === 'local' ? '0.00' : (elapsedSeconds * (0.04 / 3600)).toFixed(4);

  return (
    <Card
      elevation={0}
      sx={{
        borderRadius: 3,
        border: '1px solid',
        borderColor: 'divider',
        background: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.01)'),
        backdropFilter: 'blur(8px)',
      }}
    >
      <CardContent sx={{ p: 3 }}>
        {/* Header bar */}
        <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={2} mb={3}>
          <Box>
            <Typography variant="h5" fontWeight={700} sx={{ letterSpacing: '-0.02em' }}>
              Live Infrastructure Orchestrator
            </Typography>
            <Typography variant="body2" color="textSecondary" sx={{ mt: 0.5 }}>
              1-Click on-demand backend provisioning with automated Dynamic DNS synchronization.
            </Typography>
          </Box>

          <Box display="flex" alignItems="center" gap={1.5}>
            <Chip
              icon={backendHealthy ? <CheckCircleIcon /> : <RadioButtonCheckedIcon />}
              label={backendHealthy ? 'Backend Live' : 'Backend Standby'}
              color={backendHealthy ? 'success' : 'default'}
              variant={backendHealthy ? 'filled' : 'outlined'}
              sx={{ fontWeight: 600, px: 0.5 }}
            />
            <Button
              size="small"
              variant="outlined"
              startIcon={<RefreshIcon />}
              onClick={checkBackendHealth}
              sx={{ borderRadius: 2 }}
            >
              Ping
            </Button>
          </Box>
        </Box>

        {errorMessage && (
          <Alert severity="error" sx={{ mb: 2.5, borderRadius: 2 }} onClose={() => setErrorMessage('')}>
            {errorMessage}
          </Alert>
        )}

        {statusMessage && (
          <Alert severity="info" sx={{ mb: 2.5, borderRadius: 2 }} onClose={() => setStatusMessage('')}>
            {statusMessage}
          </Alert>
        )}

        {/* Target Switcher + Action Controls */}
        <Box
          display="flex"
          alignItems="center"
          justifyContent="space-between"
          flexWrap="wrap"
          gap={2.5}
          sx={{
            p: 2,
            borderRadius: 2.5,
            bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)'),
            border: '1px solid',
            borderColor: 'divider',
          }}
        >
          {/* Target Toggle Group */}
          <Box display="flex" flexDirection="column" gap={1}>
            <Typography variant="caption" fontWeight={600} color="textSecondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Target Environment
            </Typography>
            <ToggleButtonGroup
              value={cloudTarget}
              exclusive
              onChange={(_, val) => val && setCloudTarget(val)}
              size="small"
              sx={{
                '& .MuiToggleButton-root': {
                  px: 2,
                  py: 0.8,
                  borderRadius: 2,
                  fontWeight: 600,
                  textTransform: 'none',
                },
              }}
            >
              <ToggleButton value="azure" aria-label="Select Azure AKS">
                <CloudQueueIcon sx={{ mr: 1, fontSize: 18 }} />
                Azure AKS
              </ToggleButton>
              <ToggleButton value="aws" aria-label="Select AWS EC2">
                <StorageIcon sx={{ mr: 1, fontSize: 18 }} />
                AWS EC2
              </ToggleButton>
              <ToggleButton value="local" aria-label="Select Local Tunnel">
                <LaptopMacIcon sx={{ mr: 1, fontSize: 18 }} />
                Local Tunnel
              </ToggleButton>
            </ToggleButtonGroup>
          </Box>

          {/* Action Buttons */}
          <Box display="flex" gap={1.5} flexWrap="wrap" alignItems="center">
            <Button
              variant="contained"
              color="primary"
              size="large"
              disabled={isDeploying || isDestroying}
              onClick={handleDeploy}
              startIcon={
                isDeploying
                  ? <CircularProgress size={18} color="inherit" />
                  : (cloudTarget === 'local' ? <CloudDoneIcon /> : <CloudQueueIcon />)
              }
              sx={{
                px: 3,
                py: 1,
                borderRadius: 2,
                fontWeight: 700,
                boxShadow: 2,
              }}
            >
              {cloudTarget === 'local'
                ? 'Connect Local Backend'
                : `Launch ${cloudTarget === 'azure' ? 'Azure AKS' : 'AWS EC2'}`}
            </Button>

            <Button
              variant="outlined"
              color="error"
              size="large"
              disabled={isDeploying || isDestroying}
              onClick={handleDestroy}
              startIcon={
                isDestroying
                  ? <CircularProgress size={18} color="inherit" />
                  : <PowerSettingsNewIcon />
              }
              sx={{
                px: 2.5,
                py: 1,
                borderRadius: 2,
                fontWeight: 600,
              }}
            >
              {cloudTarget === 'local' ? 'Disconnect' : 'Tear Down ($0 Cost)'}
            </Button>
          </Box>
        </Box>

        {/* Live Metrics Row */}
        {sessionStartTime && backendHealthy && (
          <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: '1fr 1fr' }} gap={2} mt={2.5}>
            <Paper
              elevation={0}
              sx={{
                p: 1.5,
                borderRadius: 2,
                border: '1px solid',
                borderColor: 'divider',
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
              }}
            >
              <AccessTimeIcon color="primary" />
              <Box>
                <Typography variant="caption" color="textSecondary">Active Session Duration</Typography>
                <Typography variant="body1" fontWeight={700}>{formatElapsed(elapsedSeconds)}</Typography>
              </Box>
            </Paper>

            <Paper
              elevation={0}
              sx={{
                p: 1.5,
                borderRadius: 2,
                border: '1px solid',
                borderColor: 'divider',
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
              }}
            >
              <AttachMoneyIcon color={cloudTarget === 'local' ? 'success' : 'warning'} />
              <Box>
                <Typography variant="caption" color="textSecondary">Current Cloud Spend</Typography>
                <Typography variant="body1" fontWeight={700} color={cloudTarget === 'local' ? 'success.main' : 'warning.main'}>
                  {cloudTarget === 'local' ? '$0.00 (Local Machine)' : `~$${estimatedCost} USD`}
                </Typography>
              </Box>
            </Paper>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

export default DeployControl;
