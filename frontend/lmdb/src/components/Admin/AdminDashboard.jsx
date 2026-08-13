import React from 'react';
import { Navigate } from 'react-router-dom';
import { Typography, Box, CircularProgress, Paper, Chip } from '@mui/material';
import { useSelector } from 'react-redux';
import HubIcon from '@mui/icons-material/Hub';
import MovieIcon from '@mui/icons-material/Movie';
import PersonIcon from '@mui/icons-material/Person';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import PermMediaIcon from '@mui/icons-material/PermMedia';
import SecurityIcon from '@mui/icons-material/Security';
import SettingsSuggestIcon from '@mui/icons-material/SettingsSuggest';

import { userSelector } from '../../features/auth';
import { useGetProfileQuery } from '../../services/user';
import { getApiUrl } from '../../utils/apiUrl';
import StatusCard from './StatusCard';

const discoveryUrl = import.meta.env.VITE_DISCOVERY_URL || 'http://localhost:8761';
const kibanaUrl = import.meta.env.VITE_KIBANA_URL || 'http://localhost:5601';
const grafanaUrl = import.meta.env.VITE_GRAFANA_URL;

const CORE_SERVICES = [
  { name: 'Gateway', icon: <SecurityIcon sx={{ fontSize: 14 }} />, port: '8080' },
  { name: 'User & Auth', icon: <PersonIcon sx={{ fontSize: 14 }} />, port: '8082' },
  { name: 'Movie Catalog', icon: <MovieIcon sx={{ fontSize: 14 }} />, port: '8081' },
  { name: 'Actors & Cast', icon: <PersonIcon sx={{ fontSize: 14 }} />, port: '8083' },
  { name: 'AI Assistant', icon: <SmartToyIcon sx={{ fontSize: 14 }} />, port: '8084' },
  { name: 'Media Storage', icon: <PermMediaIcon sx={{ fontSize: 14 }} />, port: '8085' },
  { name: 'Config Server', icon: <SettingsSuggestIcon sx={{ fontSize: 14 }} />, port: '8888' },
  { name: 'Service Registry', icon: <HubIcon sx={{ fontSize: 14 }} />, port: '8761' },
];

/**
 * Modern Executive Ops Center with pixel-perfect responsive CSS Grid layout.
 * Visible only to users with role `ADMIN`.
 */
function AdminDashboard() {
  const { isAuthenticated, user } = useSelector(userSelector);
  const hasStoredSession = !!localStorage.getItem('access_token');

  const { data: profile, isLoading, isError } = useGetProfileQuery(undefined, {
    skip: !hasStoredSession || isAuthenticated,
  });

  if (!hasStoredSession || isError) {
    return <Navigate to="/" replace />;
  }

  if (!isAuthenticated && isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
        <CircularProgress />
      </Box>
    );
  }

  const role = isAuthenticated ? user?.role : profile?.role;
  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  const lmdbApiUrl = getApiUrl();

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4, pb: 8, width: '100%' }}>
      {/* 1. Unified Page Header */}
      <Box>
        <Typography variant="h4" fontWeight={800} sx={{ letterSpacing: '-0.03em', mb: 0.5 }}>
          Admin Dashboard
        </Typography>
        <Typography variant="body1" color="textSecondary">
          Central operations center for microservice telemetry, service topology, and cluster observability.
        </Typography>
      </Box>

      {/* 2. Observability & Infrastructure Hub (2-Column Balanced Grid) */}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        <Box>
          <Typography variant="h6" fontWeight={700} sx={{ mb: 0.5 }}>
            Infrastructure &amp; Observability Hub
          </Typography>
          <Typography variant="body2" color="textSecondary">
            Live status, routing metrics, and distributed monitoring consoles.
          </Typography>
        </Box>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
            gap: 3,
            width: '100%',
          }}
        >
          <StatusCard
            title="Discovery (Eureka)"
            description="Service registry & dynamic microservices instance discovery."
            url={discoveryUrl}
          />
          <StatusCard
            title="API Gateway"
            description="Central entry point, JWT authentication & route filter."
            url={`${lmdbApiUrl}/actuator/health`}
            secondaryUrl={`${lmdbApiUrl}/actuator/gateway/routes`}
            secondaryLabel="Routes"
          />
          <StatusCard
            title="Kibana"
            description="Distributed log search & visualization across all services."
            url={kibanaUrl}
          />
          <StatusCard
            title={grafanaUrl ? 'Grafana' : 'Metrics'}
            description={
              grafanaUrl
                ? 'Real-time performance dashboards & alerts.'
                : 'Prometheus performance metrics and service telemetries.'
            }
            url={grafanaUrl || `${lmdbApiUrl}/actuator/prometheus`}
          />
        </Box>
      </Box>

      {/* 4. Registered Microservices Mesh Topology Strip */}
      <Paper
        elevation={0}
        sx={{
          p: 3,
          borderRadius: 3,
          border: '1px solid',
          borderColor: 'divider',
          background: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.01)'),
          width: '100%',
        }}
      >
        <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'text.secondary' }}>
          Registered Service Mesh Topology
        </Typography>
        <Box display="flex" flexWrap="wrap" gap={1.2}>
          {CORE_SERVICES.map((svc) => (
            <Chip
              key={svc.name}
              icon={svc.icon}
              label={`${svc.name} (:${svc.port})`}
              size="medium"
              variant="outlined"
              sx={{
                borderRadius: 2,
                px: 0.8,
                py: 0.5,
                fontWeight: 600,
                fontSize: '0.8rem',
              }}
            />
          ))}
        </Box>
      </Paper>
    </Box>
  );
}

export default AdminDashboard;
