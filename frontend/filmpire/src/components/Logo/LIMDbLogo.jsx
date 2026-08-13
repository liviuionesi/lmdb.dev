import React from 'react';
import { useTheme } from '@mui/material/styles';

/**
 * LIMDb (Liviu Ionesi Movies Database) vector brandmark.
 *
 * @param {Object} props
 * @param {number|string} [props.width=180]
 * @param {number|string} [props.height=44]
 * @param {string} [props.className]
 * @param {'full'|'compact'} [props.variant='full']
 */
function LIMDbLogo({ width = 180, height = 44, className = '', variant = 'full' }) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const badgeBg = isDark ? '#E50914' : '#E50914'; // Crimson Cine Accent
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
        data-testid="limdb-logo"
        aria-label="LIMDb Logo"
      >
        <rect width="44" height="44" rx="10" fill={goldAccent} />
        <path d="M7 10H13V30H23V34H7V10Z" fill="#121212" />
        <rect x="26" y="10" width="11" height="24" rx="2" fill="#121212" />
        <circle cx="31.5" cy="15" r="2" fill={goldAccent} />
      </svg>
    );
  }

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 220 54"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      data-testid="limdb-logo"
      aria-label="LIMDb - Liviu Ionesi Movies Database"
    >
      <defs>
        <linearGradient id="limdbGoldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#FFD700" />
          <stop offset="100%" stopColor="#E5A00D" />
        </linearGradient>
        <linearGradient id="limdbRedGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#FF334B" />
          <stop offset="100%" stopColor="#B3001B" />
        </linearGradient>
        <filter id="badgeShadow" x="-10%" y="-10%" width="130%" height="130%">
          <feDropShadow dx="0" dy="2" stdDeviation="3" floodOpacity="0.25" />
        </filter>
      </defs>

      {/* Gold LIMDb Emblem Box */}
      <rect
        x="2"
        y="4"
        width="114"
        height="46"
        rx="8"
        fill="url(#limdbGoldGrad)"
        filter="url(#badgeShadow)"
      />

      {/* Film Perforations on top & bottom edge of badge */}
      <rect x="10" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="22" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="34" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="46" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="58" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="70" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="82" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="94" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />
      <rect x="104" y="7" width="5" height="3" rx="1" fill="#121212" fillOpacity="0.4" />

      {/* Bold LIMDb Text Inside Badge */}
      <text
        x="59"
        y="37"
        fontFamily="'Outfit', 'Inter', 'Impact', sans-serif"
        fontWeight="900"
        fontSize="28"
        fill="#0A0A0A"
        textAnchor="middle"
        letterSpacing="0.5"
      >
        LIMDb
      </text>

      {/* Right Brand Text: LIVIU IONESI / MOVIES DB */}
      <text
        x="126"
        y="23"
        fontFamily="'Inter', 'Roboto', sans-serif"
        fontWeight="800"
        fontSize="13.5"
        fill={textColor}
        letterSpacing="1"
      >
        LIVIU IONESI
      </text>
      <text
        x="126"
        y="41"
        fontFamily="'Inter', 'Roboto', sans-serif"
        fontWeight="600"
        fontSize="10.5"
        fill={subtitleColor}
        letterSpacing="2.5"
      >
        MOVIES DB
      </text>
    </svg>
  );
}

export default LIMDbLogo;
