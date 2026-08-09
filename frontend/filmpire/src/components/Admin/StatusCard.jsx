import React from 'react';
import {
  Card,
  CardContent,
  Button,
  Typography,
  Box,
  Tooltip,
  Chip,
  Avatar,
} from '@mui/material';
import { OpenInNew } from '@mui/icons-material';
import HubIcon from '@mui/icons-material/Hub';
import RouterIcon from '@mui/icons-material/Router';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import SpeedIcon from '@mui/icons-material/Speed';
import DnsIcon from '@mui/icons-material/Dns';

import useServiceStatus from './useServiceStatus';
import useStyles from './styles';

const STATUS_LABEL = {
  checking: 'Checking…',
  up: 'Reachable',
  down: 'Unreachable',
  unknown: 'Not configured',
};

const STATUS_COLOR = {
  checking: 'default',
  up: 'success',
  down: 'error',
  unknown: 'default',
};

const getServiceMeta = (title) => {
  const t = title.toLowerCase();
  if (t.includes('discovery') || t.includes('eureka')) {
    return { icon: <HubIcon fontSize="small" />, color: '#10b981', bg: 'rgba(16, 185, 129, 0.12)', port: '8761' };
  }
  if (t.includes('gateway')) {
    return { icon: <RouterIcon fontSize="small" />, color: '#3b82f6', bg: 'rgba(59, 130, 246, 0.12)', port: '8080' };
  }
  if (t.includes('kibana') || t.includes('log')) {
    return { icon: <AnalyticsIcon fontSize="small" />, color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.12)', port: '5601' };
  }
  if (t.includes('metrics') || t.includes('grafana')) {
    return { icon: <SpeedIcon fontSize="small" />, color: '#8b5cf6', bg: 'rgba(139, 92, 246, 0.12)', port: 'Prometheus' };
  }
  return { icon: <DnsIcon fontSize="small" />, color: '#6366f1', bg: 'rgba(99, 102, 241, 0.12)', port: 'Service' };
};

/**
 * Modern SaaS Infrastructure Bento Tile with integrated header actions.
 */
function StatusCard({ title, description, url, secondaryUrl, secondaryLabel }) {
  const { classes } = useStyles();
  const status = useServiceStatus(url);
  const meta = getServiceMeta(title);

  const dotClass = {
    checking: classes.statusChecking,
    up: classes.statusUp,
    down: classes.statusDown,
    unknown: classes.statusChecking,
  }[status];

  return (
    <Card
      elevation={0}
      sx={{
        borderRadius: 3,
        border: '1px solid',
        borderColor: 'divider',
        background: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.01)'),
        backdropFilter: 'blur(8px)',
        transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
        '&:hover': {
          borderColor: meta.color,
          boxShadow: `0 8px 24px -4px ${meta.bg}`,
        },
      }}
    >
      <CardContent sx={{ p: 2.5, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        {/* Header with Icon, Title, Port & Action */}
        <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1.5}>
          <Box display="flex" alignItems="center" gap={1.5}>
            <Avatar
              sx={{
                bgcolor: meta.bg,
                color: meta.color,
                width: 38,
                height: 38,
                borderRadius: 2,
              }}
            >
              {meta.icon}
            </Avatar>
            <Box>
              <Box display="flex" alignItems="center" gap={1}>
                <Typography variant="subtitle1" fontWeight={700} sx={{ lineHeight: 1.2 }}>
                  {title}
                </Typography>
                <Chip
                  label={meta.port}
                  size="small"
                  sx={{
                    height: 18,
                    fontSize: '0.65rem',
                    fontWeight: 700,
                    bgcolor: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)'),
                  }}
                />
              </Box>
            </Box>
          </Box>

          <Box display="flex" alignItems="center" gap={1.5}>
            <Box display="flex" alignItems="center" gap={0.8}>
              <Tooltip title={STATUS_LABEL[status]}>
                <span className={`${classes.statusDot} ${dotClass}`} aria-label={STATUS_LABEL[status]} />
              </Tooltip>
              <Chip
                size="small"
                label={status === 'up' ? 'Online' : (status === 'down' ? 'Offline' : 'Standby')}
                color={STATUS_COLOR[status]}
                variant={status === 'up' ? 'filled' : 'outlined'}
                sx={{
                  fontSize: '0.7rem',
                  height: 22,
                  fontWeight: 700,
                  borderRadius: 1.5,
                }}
              />
            </Box>

            <Box display="flex" gap={1}>
              <Button
                size="small"
                variant="outlined"
                endIcon={<OpenInNew sx={{ fontSize: 13 }} />}
                disabled={!url}
                component="a"
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                sx={{
                  borderRadius: 2,
                  px: 1.8,
                  py: 0.4,
                  fontWeight: 600,
                  textTransform: 'none',
                }}
              >
                Open
              </Button>
              {secondaryUrl && (
                <Button
                  size="small"
                  variant="text"
                  endIcon={<OpenInNew sx={{ fontSize: 13 }} />}
                  component="a"
                  href={secondaryUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  sx={{
                    borderRadius: 2,
                    px: 1.2,
                    py: 0.4,
                    fontWeight: 600,
                    textTransform: 'none',
                  }}
                >
                  {secondaryLabel}
                </Button>
              )}
            </Box>
          </Box>
        </Box>

        <Typography variant="body2" color="textSecondary" sx={{ lineHeight: 1.6 }}>
          {description}
        </Typography>
      </CardContent>
    </Card>
  );
}

export default StatusCard;
