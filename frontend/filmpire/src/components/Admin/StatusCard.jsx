import React from 'react';
import { Card, CardContent, CardActions, Button, Typography, Box, Tooltip, Chip } from '@mui/material';
import { OpenInNew } from '@mui/icons-material';

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

/**
 * Modern SaaS Ops Card with live health chip and quick launch links.
 */
function StatusCard({ title, description, url, secondaryUrl, secondaryLabel }) {
  const { classes } = useStyles();
  const status = useServiceStatus(url);

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
        borderRadius: 2.5,
        border: '1px solid',
        borderColor: 'divider',
        transition: 'all 0.2s ease-in-out',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: 2,
        },
      }}
    >
      <CardContent sx={{ p: 2.5, flexGrow: 1 }}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={1.5}>
          <Box display="flex" alignItems="center" gap={1}>
            <Tooltip title={STATUS_LABEL[status]}>
              <span className={`${classes.statusDot} ${dotClass}`} aria-label={STATUS_LABEL[status]} />
            </Tooltip>
            <Typography variant="subtitle1" fontWeight={700}>
              {title}
            </Typography>
          </Box>
          <Chip
            size="small"
            label={status === 'up' ? 'Online' : (status === 'down' ? 'Offline' : 'Standby')}
            color={STATUS_COLOR[status]}
            variant="outlined"
            sx={{ fontSize: '0.7rem', height: 20, fontWeight: 600 }}
          />
        </Box>

        <Typography variant="body2" color="textSecondary" sx={{ lineHeight: 1.5 }}>
          {description}
        </Typography>
      </CardContent>

      <CardActions sx={{ p: 2, pt: 0, gap: 1 }}>
        <Button
          size="small"
          variant="outlined"
          endIcon={<OpenInNew sx={{ fontSize: 14 }} />}
          disabled={!url}
          component="a"
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          sx={{ borderRadius: 1.5, textTransform: 'none', fontWeight: 600 }}
        >
          Open
        </Button>
        {secondaryUrl && (
          <Button
            size="small"
            variant="text"
            endIcon={<OpenInNew sx={{ fontSize: 14 }} />}
            component="a"
            href={secondaryUrl}
            target="_blank"
            rel="noopener noreferrer"
            sx={{ borderRadius: 1.5, textTransform: 'none', fontWeight: 600 }}
          >
            {secondaryLabel}
          </Button>
        )}
      </CardActions>
    </Card>
  );
}

export default StatusCard;
