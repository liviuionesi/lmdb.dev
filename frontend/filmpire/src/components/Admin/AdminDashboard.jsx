import React from 'react';
import { Navigate } from 'react-router-dom';
import { Typography, Grid, Box, CircularProgress } from '@mui/material';
import { useSelector } from 'react-redux';

import { userSelector } from '../../features/auth';
import { useGetProfileQuery } from '../../services/user';
import StatusCard from './StatusCard';
import DeployControl from './DeployControl';

const filmpireApiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const discoveryUrl = import.meta.env.VITE_DISCOVERY_URL || 'http://localhost:8761';
const kibanaUrl = import.meta.env.VITE_KIBANA_URL || 'http://localhost:5601';
const grafanaUrl = import.meta.env.VITE_GRAFANA_URL;

/**
 * Modern Executive Ops Center.
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

  return (
    <Box sx={{ pb: 6 }}>
      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" fontWeight={700} sx={{ letterSpacing: '-0.02em', mb: 0.5 }}>
          Admin Dashboard
        </Typography>
        <Typography variant="body1" color="textSecondary">
          Live orchestration, telemetry, and observability for Filmpire microservices.
        </Typography>
      </Box>

      {/* Cloud & Local 1-Click Orchestrator */}
      <DeployControl apiUrl={filmpireApiUrl} />

      {/* Infrastructure Mesh Section */}
      <Box sx={{ mt: 4 }}>
        <Typography variant="h6" fontWeight={700} sx={{ mb: 2 }}>
          Infrastructure Services
        </Typography>

        <Grid container spacing={2.5}>
          <Grid
            size={{
              xs: 12,
              sm: 6,
              md: 3,
            }}
          >
            <StatusCard
              title="Discovery (Eureka)"
              description="Service registry & dynamic microservices instance discovery."
              url={discoveryUrl}
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              sm: 6,
              md: 3,
            }}
          >
            <StatusCard
              title="API Gateway"
              description="Central entry point, JWT authentication & route filter."
              url={`${filmpireApiUrl}/actuator/health`}
              secondaryUrl={`${filmpireApiUrl}/actuator/gateway/routes`}
              secondaryLabel="Routes"
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              sm: 6,
              md: 3,
            }}
          >
            <StatusCard
              title="Kibana"
              description="Distributed log search & visualization across all services."
              url={kibanaUrl}
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              sm: 6,
              md: 3,
            }}
          >
            <StatusCard
              title={grafanaUrl ? 'Grafana' : 'Metrics'}
              description={
                grafanaUrl
                  ? 'Real-time performance dashboards & alerts.'
                  : 'Prometheus performance metrics and service telemetries.'
              }
              url={grafanaUrl || `${filmpireApiUrl}/actuator/prometheus`}
            />
          </Grid>
        </Grid>
      </Box>
    </Box>
  );
}

export default AdminDashboard;
