import React from 'react';

/**
 * Official The Movie Database (TMDB) vector attribution mark.
 *
 * @param {Object} props
 * @param {number|string} [props.width=90]
 * @param {number|string} [props.height=20]
 * @param {string} [props.className]
 */
function TMDBLogo({ width = 90, height = 20, className = '' }) {
  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 180 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      data-testid="tmdb-logo"
      aria-label="The Movie Database (TMDB)"
    >
      <defs>
        <linearGradient id="tmdbGradient" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#90CEA1" />
          <stop offset="50%" stopColor="#01B4E4" />
          <stop offset="100%" stopColor="#0D253F" />
        </linearGradient>
      </defs>
      {/* Pill Badge */}
      <rect width="180" height="40" rx="8" fill="url(#tmdbGradient)" />
      {/* TMDB Text */}
      <text
        x="90"
        y="27"
        fontFamily="'Source Sans Pro', 'Inter', 'Roboto', sans-serif"
        fontWeight="900"
        fontSize="22"
        fill="#FFFFFF"
        textAnchor="middle"
        letterSpacing="2"
      >
        TMDB
      </text>
    </svg>
  );
}

export default TMDBLogo;
