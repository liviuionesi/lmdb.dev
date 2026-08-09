import React from 'react';
import { Navigate } from 'react-router-dom';
import { Typography, Grid, Box, CircularProgress } from '@mui/material';
import { useSelector } from 'react-redux';

import { userSelector } from '../../features/auth';
import { useGetProfileQuery } from '../../services/user';
import StatusCard from './StatusCard';

const filmpireApiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const discoveryUrl = import.meta.env.VITE_DISCOVERY_URL || 'http://localhost:8761';
const kibanaUrl = import.meta.env.VITE_KIBANA_URL || 'http://localhost:5601';
const grafanaUrl = import.meta.env.VITE_GRAFANA_URL;

/**
 * Read-only ops view: one card per infrastructure tool this stack runs
 * (Eureka, the gateway, Kibana, metrics), each with a live reachability
 * check and a link out. Visible only to users whose profile role is
 * `ADMIN` — everyone else is redirected to the home page.
 *
 * Promoting an account to `ADMIN` currently requires a manual DB update —
 * there is no account-management UI for it yet.
 */
const AdminDashboard = () => {
  const { isAuthenticated, user } = useSelector(userSelector);
  const hasStoredSession = !!localStorage.getItem('access_token');

  // On a fresh page load (bookmark, refresh) the Redux auth state starts
  // empty even for a logged-in user — NavBar restores it from the stored
  // JWT via this same query, asynchronously, in its own effect. Reading
  // `profile` straight from this component's own query result (rather
  // than waiting on Redux `user` to catch up through NavBar's effect)
  // avoids a redirect firing on the not-yet-hydrated initial render.
  const { data: profile, isLoading, isError } = useGetProfileQuery(undefined, {
    skip: !hasStoredSession || isAuthenticated,
  });

  if (!hasStoredSession || isError) {
    return <Navigate to="/" replace />;
  }

  if (!isAuthenticated && isLoading) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          mt: 4,
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  const role = isAuthenticated ? user?.role : profile?.role;
  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Admin Dashboard</Typography>
      <Typography variant="body2" color="textSecondary" sx={{ mb: 3 }}>
        Live links to the infrastructure this stack runs locally. The green/red
        dot only means &quot;something answered on that port&quot; — it isn&apos;t
        a deep health check.
      </Typography>
      <Grid container spacing={2}>
        <Grid
          size={{
            xs: 12,
            sm: 6,
            md: 3,
          }}
        >
          <StatusCard
            title="Discovery (Eureka)"
            description="Service registry — which instances are registered and up."
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
            description="Gateway health and routing."
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
            description="Centralized logs (ELK stack)."
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
                ? 'Dashboards and alerts (deployed stack).'
                : 'Raw Prometheus metrics from the gateway. Grafana itself only '
                  + 'runs on the minikube/k8s deployment — set VITE_GRAFANA_URL '
                  + 'to link there instead.'
            }
            url={grafanaUrl || `${filmpireApiUrl}/actuator/prometheus`}
          />
        </Grid>
      </Grid>
    </Box>
  );
};

export default AdminDashboard;
