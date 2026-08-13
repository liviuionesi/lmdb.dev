import React from 'react';
import { useTheme } from '@mui/material/styles';

/**
 * LMDB (Liviu Movies Database) vector brandmark.
 *
 * @param {Object} props
 * @param {number|string} [props.width=180]
 * @param {number|string} [props.height=44]
 * @param {string} [props.className]
 * @param {'full'|'compact'} [props.variant='full']
 */
function LMDBLogo({ width = 180, height = 44, className = '', variant = 'full' }) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const goldAccent = '#F5C518'; // Iconic Cinema Gold
  const textColor = isDark ? '#FFFFFF' : '#141414';
  const subtitleColor = isDark ? '#A3A3A3' : '#666666';

  if (variant === 'compact') {
    return (
      <svg
        width={height}
        height={height}
        viewBox="0 0 44 44"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className={className}
        data-testid="lmdb-logo"
        aria-label="LMDB Logo"
      >
        <rect width="44" height="44" rx="10" fill={goldAccent} />
        <path d="M8 10H14V30H24V34H8V10Z" fill="#121212" />
        <rect x="27" y="10" width="10" height="24" rx="2" fill="#121212" />
      </svg>
    );
  }

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 210 54"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      data-testid="lmdb-logo"
      aria-label="LMDB - Liviu Movies Database"
    >
      <defs>
        <linearGradient id="lmdbGoldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#FFD700" />
          <stop offset="100%" stopColor="#E5A00D" />
        </linearGradient>
        <filter id="badgeShadow" x="-10%" y="-10%" width="130%" height="130%">
          <feDropShadow dx="0" dy="2" stdDeviation="3" floodOpacity="0.25" />
        </filter>
      </defs>

      {/* Gold LMDB Cinema Badge */}
      <rect
        x="2"
        y="4"
        width="106"
        height="46"
        rx="8"
        fill="url(#lmdbGoldGrad)"
        filter="url(#badgeShadow)"
      />

      {/* Film Perforations */}
      <rect x="9" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="20" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="31" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="42" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="53" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="64" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="75" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="86" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="96" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />

      {/* Bold LMDB Text Inside Badge */}
      <text
        x="55"
        y="37"
        fontFamily="'Outfit', 'Inter', 'Impact', sans-serif"
        fontWeight="900"
        fontSize="28"
        fill="#0A0A0A"
        textAnchor="middle"
        letterSpacing="0.8"
      >
        LMDB
      </text>

      {/* Right Brand Text: LIVIU / MOVIES DB */}
      <text
        x="118"
        y="23"
        fontFamily="'Inter', 'Roboto', sans-serif"
        fontWeight="800"
        fontSize="14"
        fill={textColor}
        letterSpacing="1.2"
      >
        LIVIU
      </text>
      <text
        x="118"
        y="41"
        fontFamily="'Inter', 'Roboto', sans-serif"
        fontWeight="600"
        fontSize="10.5"
        fill={subtitleColor}
        letterSpacing="2"
      >
        MOVIES DB
      </text>
    </svg>
  );
}

export default LMDBLogo;
