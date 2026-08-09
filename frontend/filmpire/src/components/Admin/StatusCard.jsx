import React from 'react';
import {
  Card,
  CardContent,
  CardActions,
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

const getServiceIcon = (title) => {
  const t = title.toLowerCase();
  if (t.includes('discovery') || t.includes('eureka')) {
    return { icon: <HubIcon fontSize="small" />, color: '#10b981', bg: 'rgba(16, 185, 129, 0.12)' };
  }
  if (t.includes('gateway')) {
    return { icon: <RouterIcon fontSize="small" />, color: '#3b82f6', bg: 'rgba(59, 130, 246, 0.12)' };
  }
  if (t.includes('kibana') || t.includes('log')) {
    return { icon: <AnalyticsIcon fontSize="small" />, color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.12)' };
  }
  if (t.includes('metrics') || t.includes('grafana')) {
    return { icon: <SpeedIcon fontSize="small" />, color: '#8b5cf6', bg: 'rgba(139, 92, 246, 0.12)' };
  }
  return { icon: <DnsIcon fontSize="small" />, color: '#6366f1', bg: 'rgba(99, 102, 241, 0.12)' };
};

/**
 * Modern SaaS Infrastructure Bento Tile.
 * Displays live service health, dedicated service iconography, and direct console links.
 */
function StatusCard({ title, description, url, secondaryUrl, secondaryLabel }) {
  const { classes } = useStyles();
  const status = useServiceStatus(url);
  const iconConfig = getServiceIcon(title);

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
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderRadius: 3,
        border: '1px solid',
        borderColor: 'divider',
        background: (theme) => (theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.01)'),
        backdropFilter: 'blur(8px)',
        transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
        '&:hover': {
          transform: 'translateY(-3px)',
          borderColor: iconConfig.color,
          boxShadow: `0 8px 24px -4px ${iconConfig.bg}`,
        },
      }}
    >
      <CardContent sx={{ p: 2.5, flexGrow: 1 }}>
        {/* Header with Icon + Status */}
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Box display="flex" alignItems="center" gap={1.5}>
            <Avatar
              sx={{
                bgcolor: iconConfig.bg,
                color: iconConfig.color,
                width: 36,
                height: 36,
                borderRadius: 2,
              }}
            >
              {iconConfig.icon}
            </Avatar>
            <Box>
              <Typography variant="subtitle1" fontWeight={700} sx={{ lineHeight: 1.2 }}>
                {title}
              </Typography>
            </Box>
          </Box>

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
                fontSize: '0.68rem',
                height: 20,
                fontWeight: 700,
                letterSpacing: '0.02em',
                borderRadius: 1.5,
              }}
            />
          </Box>
        </Box>

        <Typography variant="body2" color="textSecondary" sx={{ lineHeight: 1.6 }}>
          {description}
        </Typography>
      </CardContent>

      <CardActions sx={{ p: 2.5, pt: 0, gap: 1, display: 'flex', justifyContent: 'flex-start' }}>
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
            py: 0.5,
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
              px: 1.5,
              py: 0.5,
              fontWeight: 600,
              textTransform: 'none',
            }}
          >
            {secondaryLabel}
          </Button>
        )}
      </CardActions>
    </Card>
  );
}

export default StatusCard;
