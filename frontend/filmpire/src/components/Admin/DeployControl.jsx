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
  CircularProgress,
  Chip,
  Divider,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  TextField,
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import CableIcon from '@mui/icons-material/Cable';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

const STEPS = [
  'Select Target',
  'Trigger Workflow / Launch',
  'Provisioning Cloud / Cluster',
  'Deploying Microservices',
  'DNS Synced & Backend Live',
];

const REPO_OWNER = 'pehlivanu';
const REPO_NAME = 'filmpire-microservices';

/**
 * 1-Click Zero-Touch Cloud & Local Deployment Control Center.
 * Provides 1-click automated provisioning and teardown with zero manual configuration.
 *
 * @author Filmpire Development Team
 * @version 1.2.0
 */
function DeployControl({ apiUrl }) {
  const [cloudTarget, setCloudTarget] = useState('azure');
  const [githubToken, setGithubToken] = useState(() => import.meta.env.VITE_GITHUB_TOKEN || localStorage.getItem('filmpire_gh_token') || '');
  const [customTunnelUrl, setCustomTunnelUrl] = useState(() => localStorage.getItem('filmpire_tunnel_url') || '');
  const [activeStep, setActiveStep] = useState(0);
  const [isDeploying, setIsDeploying] = useState(false);
  const [isDestroying, setIsDestroying] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [backendHealthy, setBackendHealthy] = useState(false);
  const [sessionStartTime, setSessionStartTime] = useState(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  const effectiveApiUrl = (cloudTarget === 'local' && customTunnelUrl) ? customTunnelUrl : apiUrl;

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

  const dispatchWorkflow = async (workflowFile, inputs = {}) => {
    setErrorMessage('');
    const token = githubToken || import.meta.env.VITE_GITHUB_TOKEN || localStorage.getItem('filmpire_gh_token');

    if (!token) {
      setErrorMessage('GitHub Personal Access Token or VITE_GITHUB_TOKEN environment variable is required to trigger automated cloud deployment.');
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
      setStatusMessage('Local tunnel connected! Verifying gateway health...');
      setActiveStep(4);
      await checkBackendHealth();
      return;
    }

    setIsDeploying(true);
    setStatusMessage(`Triggering automated ${cloudTarget.toUpperCase()} deployment workflow...`);
    setActiveStep(1);

    const success = await dispatchWorkflow('deploy.yml', { cloud: cloudTarget });
    if (success) {
      setStatusMessage(`Automated deployment dispatched for ${cloudTarget.toUpperCase()}! Provisioning cloud infrastructure and linking DNS...`);
      setActiveStep(2);
      setTimeout(() => setActiveStep(3), 8000);
    } else {
      setActiveStep(0);
    }
    setIsDeploying(false);
  };

  const handleDestroy = async () => {
    if (cloudTarget === 'local') {
      setStatusMessage('Local demo stopped. Run ./infrastructure/scripts/stop-infrastructure.sh to stop local processes.');
      setSessionStartTime(null);
      setElapsedSeconds(0);
      setBackendHealthy(false);
      setActiveStep(0);
      return;
    }

    setIsDestroying(true);
    setStatusMessage(`Triggering automated ${cloudTarget.toUpperCase()} teardown workflow...`);

    const success = await dispatchWorkflow('destroy.yml', { cloud: cloudTarget });
    if (success) {
      setStatusMessage(`Teardown dispatched for ${cloudTarget.toUpperCase()}! Cloud resources will be destroyed to preserve $0 spend.`);
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
              1-Click Automated Deployment Control
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Zero-touch automated deployment for live portfolio demonstrations. Spin up Azure AKS, AWS EC2, or connect to local stack with 1 click.
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
            Select Deployment Target:
          </Typography>
          <RadioGroup
            row
            value={cloudTarget}
            onChange={(e) => setCloudTarget(e.target.value)}
          >
            <FormControlLabel
              value="azure"
              control={<Radio />}
              label="Azure AKS (Cloud 1-Click)"
            />
            <FormControlLabel
              value="aws"
              control={<Radio />}
              label="AWS EC2 (Cloud 1-Click)"
            />
            <FormControlLabel
              value="local"
              control={<Radio />}
              label="Local Stack (Zero-Touch Tunnel)"
            />
          </RadioGroup>
        </Box>

        <Box display="flex" gap={2} flexWrap="wrap" mb={3}>
          <Button
            variant="contained"
            color="primary"
            size="large"
            startIcon={
              isDeploying
                ? <CircularProgress size={20} color="inherit" />
                : (cloudTarget === 'local' ? <CableIcon /> : <CloudUploadIcon />)
            }
            onClick={handleDeploy}
            disabled={isDeploying || isDestroying}
          >
            {cloudTarget === 'local' ? 'Connect Local Backend' : `Deploy Backend to ${cloudTarget === 'azure' ? 'Azure AKS' : 'AWS k3s'}`}
          </Button>

          <Button
            variant="outlined"
            color="error"
            size="large"
            startIcon={isDestroying ? <CircularProgress size={20} color="inherit" /> : <DeleteOutlineIcon />}
            onClick={handleDestroy}
            disabled={isDeploying || isDestroying}
          >
            {cloudTarget === 'local' ? 'Disconnect Backend' : 'Tear Down Backend (Destroy)'}
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
          <Box bgcolor="action.hover" p={2} borderRadius={2} display="flex" justifyContent="space-between" flexWrap="wrap" gap={2} mb={2}>
            <Typography variant="body2">
              <strong>Active Session Time:</strong> {formatElapsed(elapsedSeconds)}
            </Typography>
            <Typography variant="body2" color={cloudTarget === 'local' ? 'success.main' : 'warning.main'}>
              <strong>Estimated Spend:</strong> {cloudTarget === 'local' ? '$0.00 (Local Free Demo)' : `~$${estimatedCost}`}
            </Typography>
          </Box>
        )}

        <Accordion sx={{ mt: 2, boxShadow: 'none', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="body2" color="textSecondary">
              Advanced Settings &amp; Token Overrides (Optional)
            </Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Box display="flex" flexDirection="column" gap={2}>
              <TextField
                label="GitHub Personal Access Token (Override)"
                type="password"
                size="small"
                value={githubToken}
                onChange={(e) => {
                  setGithubToken(e.target.value);
                  localStorage.setItem('filmpire_gh_token', e.target.value);
                }}
                placeholder="ghp_..."
                helperText="Optional: defaults to VITE_GITHUB_TOKEN environment variable."
                fullWidth
              />
              <TextField
                label="Custom API / Tunnel URL (Override)"
                size="small"
                value={customTunnelUrl}
                onChange={(e) => {
                  setCustomTunnelUrl(e.target.value);
                  localStorage.setItem('filmpire_tunnel_url', e.target.value);
                }}
                placeholder="https://filmpire-api.duckdns.org"
                helperText="Optional: defaults to VITE_API_URL or DuckDNS sync."
                fullWidth
              />
            </Box>
          </AccordionDetails>
        </Accordion>
      </CardContent>
    </Card>
  );
}

export default DeployControl;
