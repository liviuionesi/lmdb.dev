import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  RadioGroup,
  FormControlLabel,
  Radio,
  Button,
  Stepper,
  Step,
  StepLabel,
  Alert,
  TextField,
  CircularProgress,
  Chip,
  Divider,
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import CableIcon from '@mui/icons-material/Cable';

const STEPS = [
  'Select Target',
  'Trigger Workflow / Launch Tunnel',
  'Provisioning & Connecting',
  'Deploying K8s / Services',
  'DNS / Tunnel Linked & Ready',
];

const REPO_OWNER = 'pehlivanu';
const REPO_NAME = 'filmpire-microservices';

/**
 * 1-Click Cloud & Local Deployment Control Center.
 * Allows administrators to provision ephemeral Azure AKS, AWS k3s clusters,
 * or connect to a local workstation / Minikube stack via Cloudflare Tunnel.
 *
 * @author Filmpire Development Team
 * @version 1.1.0
 */
function DeployControl({ apiUrl }) {
  const [cloudTarget, setCloudTarget] = useState('azure');
  const [githubToken, setGithubToken] = useState(() => localStorage.getItem('filmpire_gh_token') || '');
  const [customTunnelUrl, setCustomTunnelUrl] = useState(() => localStorage.getItem('filmpire_tunnel_url') || '');
  const [activeStep, setActiveStep] = useState(0);
  const [isDeploying, setIsDeploying] = useState(false);
  const [isDestroying, setIsDestroying] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [backendHealthy, setBackendHealthy] = useState(false);
  const [sessionStartTime, setSessionStartTime] = useState(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  const effectiveApiUrl = cloudTarget === 'local' && customTunnelUrl ? customTunnelUrl : apiUrl;

  const checkBackendHealth = useCallback(async () => {
    try {
      const res = await fetch(`${effectiveApiUrl}/actuator/health`, { method: 'GET', mode: 'cors' });
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
  }, [effectiveApiUrl, sessionStartTime]);

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

  const handleTokenChange = (e) => {
    const val = e.target.value;
    setGithubToken(val);
    localStorage.setItem('filmpire_gh_token', val);
  };

  const handleTunnelUrlChange = (e) => {
    const val = e.target.value;
    setCustomTunnelUrl(val);
    localStorage.setItem('filmpire_tunnel_url', val);
  };

  const dispatchWorkflow = async (workflowFile, inputs = {}) => {
    setErrorMessage('');
    if (!githubToken) {
      setErrorMessage('GitHub Personal Access Token is required to trigger cloud deployment.');
      return false;
    }

    try {
      const response = await fetch(
        `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/actions/workflows/${workflowFile}/dispatches`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${githubToken}`,
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
      setStatusMessage('Checking local Cloudflare Tunnel health...');
      setActiveStep(4);
      await checkBackendHealth();
      return;
    }

    setIsDeploying(true);
    setStatusMessage(`Triggering ${cloudTarget.toUpperCase()} deployment workflow...`);
    setActiveStep(1);

    const success = await dispatchWorkflow('deploy.yml', { cloud: cloudTarget });
    if (success) {
      setStatusMessage(`Deployment workflow dispatched on GitHub for ${cloudTarget.toUpperCase()}! Provisioning cloud infrastructure...`);
      setActiveStep(2);
      setTimeout(() => setActiveStep(3), 8000);
    } else {
      setActiveStep(0);
    }
    setIsDeploying(false);
  };

  const handleDestroy = async () => {
    if (cloudTarget === 'local') {
      setStatusMessage('To stop the local tunnel, run: ./infrastructure/scripts/stop-tunnel.sh in your terminal.');
      setSessionStartTime(null);
      setElapsedSeconds(0);
      setBackendHealthy(false);
      setActiveStep(0);
      return;
    }

    setIsDestroying(true);
    setStatusMessage(`Triggering ${cloudTarget.toUpperCase()} teardown workflow...`);

    const success = await dispatchWorkflow('destroy.yml', { cloud: cloudTarget });
    if (success) {
      setStatusMessage(`Teardown workflow dispatched on GitHub for ${cloudTarget.toUpperCase()}! Destroying cloud resources...`);
      setActiveStep(0);
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
    <Card sx={{ mb: 4, p: 1 }}>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={2} mb={2}>
          <Box>
            <Typography variant="h5" fontWeight="bold" gutterBottom>
              1-Click Deployment &amp; Tunnel Control
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Connect this Vercel frontend to an ephemeral cloud backend (Azure/AWS) or a local Docker/Minikube instance via Cloudflare Tunnel.
            </Typography>
          </Box>
          <Box display="flex" alignItems="center" gap={1}>
            <Chip
              icon={backendHealthy ? <CheckCircleOutlineIcon /> : undefined}
              label={backendHealthy ? 'Backend Live 🟢' : 'Backend Offline 🔴'}
              color={backendHealthy ? 'success' : 'default'}
              variant="outlined"
            />
            <Button size="small" variant="outlined" startIcon={<RefreshIcon />} onClick={checkBackendHealth}>
              Ping
            </Button>
          </Box>
        </Box>

        <Divider sx={{ my: 2 }} />

        {errorMessage && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErrorMessage('')}>
            {errorMessage}
          </Alert>
        )}

        {statusMessage && (
          <Alert severity="info" sx={{ mb: 2 }} onClose={() => setStatusMessage('')}>
            {statusMessage}
          </Alert>
        )}

        <Box display="flex" flexDirection="column" gap={2} mb={3}>
          <Typography variant="subtitle2" fontWeight="bold">
            1. Select Deployment Target:
          </Typography>
          <RadioGroup
            row
            value={cloudTarget}
            onChange={(e) => setCloudTarget(e.target.value)}
          >
            <FormControlLabel
              value="azure"
              control={<Radio />}
              label="Azure AKS (Cloud)"
            />
            <FormControlLabel
              value="aws"
              control={<Radio />}
              label="AWS EC2 / k3s (Cloud)"
            />
            <FormControlLabel
              value="local"
              control={<Radio />}
              label="Local Docker / Minikube (Cloudflare Tunnel)"
            />
          </RadioGroup>

          {cloudTarget === 'local' ? (
            <Box bgcolor="action.hover" p={2} borderRadius={2} display="flex" flexDirection="column" gap={1.5}>
              <Typography variant="body2">
                <strong>Run local tunnel command:</strong> <code>./infrastructure/scripts/start-tunnel.sh</code>
              </Typography>
              <TextField
                label="Custom Cloudflare Tunnel URL"
                size="small"
                value={customTunnelUrl}
                onChange={handleTunnelUrlChange}
                placeholder="https://xxxx-xxxx.trycloudflare.com"
                helperText="Paste the public trycloudflare.com URL printed by start-tunnel.sh"
                fullWidth
              />
            </Box>
          ) : (
            <TextField
              label="GitHub Personal Access Token (PAT)"
              type="password"
              size="small"
              value={githubToken}
              onChange={handleTokenChange}
              placeholder="ghp_..."
              helperText="Stored locally in browser localStorage. Requires 'actions:write' scope to trigger deploy workflows."
              fullWidth
            />
          )}
        </Box>

        <Box display="flex" gap={2} flexWrap="wrap" mb={3}>
          <Button
            variant="contained"
            color="primary"
            startIcon={
              isDeploying
                ? <CircularProgress size={20} color="inherit" />
                : (cloudTarget === 'local' ? <CableIcon /> : <CloudUploadIcon />)
            }
            onClick={handleDeploy}
            disabled={isDeploying || isDestroying}
          >
            {cloudTarget === 'local' ? 'Connect Local Tunnel' : `Deploy Backend to ${cloudTarget === 'azure' ? 'Azure AKS' : 'AWS k3s'}`}
          </Button>

          <Button
            variant="outlined"
            color="error"
            startIcon={isDestroying ? <CircularProgress size={20} color="inherit" /> : <DeleteOutlineIcon />}
            onClick={handleDestroy}
            disabled={isDeploying || isDestroying}
          >
            {cloudTarget === 'local' ? 'Disconnect Tunnel' : 'Tear Down Backend (Destroy)'}
          </Button>
        </Box>

        <Stepper activeStep={activeStep} alternativeLabel sx={{ my: 3 }}>
          {STEPS.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>

        {sessionStartTime && backendHealthy && (
          <Box bgcolor="action.hover" p={2} borderRadius={2} display="flex" justifyContent="space-between" flexWrap="wrap" gap={2}>
            <Typography variant="body2">
              <strong>Active Session Time:</strong> {formatElapsed(elapsedSeconds)}
            </Typography>
            <Typography variant="body2" color={cloudTarget === 'local' ? 'success.main' : 'warning.main'}>
              <strong>Estimated Spend:</strong> {cloudTarget === 'local' ? '$0.00 (Local Free Tunnel)' : `~$${estimatedCost}`}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

export default DeployControl;
