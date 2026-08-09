import React from 'react';
import { Card, CardContent, CardActions, Button, Typography, Box, Tooltip } from '@mui/material';
import { OpenInNew } from '@mui/icons-material';

import useServiceStatus from './useServiceStatus';
import useStyles from './styles';

const STATUS_LABEL = {
  checking: 'Checking…',
  up: 'Reachable',
  down: 'Unreachable',
  unknown: 'Not configured',
};

/**
 * One ops-tool tile on the admin dashboard: a live reachability dot, a
 * short description, and an "Open" link to the tool itself.
 *
 * @param {string} title tool name (e.g. "Discovery (Eureka)")
 * @param {string} description one-line explanation of what this tool is
 * @param {string} [url] absolute URL to link to and probe; omitted renders
 *   the card as "not configured" (e.g. Grafana with no env var set)
 * @param {string} [secondaryUrl] optional second link (e.g. gateway routes)
 * @param {string} [secondaryLabel] label for secondaryUrl
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
    <Card className={classes.card} variant="outlined">
      <CardContent>
        <Box className={classes.cardHeader}>
          <Tooltip title={STATUS_LABEL[status]}>
            <span className={`${classes.statusDot} ${dotClass}`} />
          </Tooltip>
          <Typography variant="h6">{title}</Typography>
        </Box>
        <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
          {description}
        </Typography>
      </CardContent>
      <CardActions className={classes.cardActions}>
        <Button
          size="small"
          endIcon={<OpenInNew />}
          disabled={!url}
          component="a"
          href={url}
          target="_blank"
          rel="noopener noreferrer"
        >
          Open
        </Button>
        {secondaryUrl && (
          <Button
            size="small"
            endIcon={<OpenInNew />}
            component="a"
            href={secondaryUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            {secondaryLabel}
          </Button>
        )}
      </CardActions>
    </Card>
  );
}

export default StatusCard;
