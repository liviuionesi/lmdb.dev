package com.filmpire.media.model;

/**
 * Enumeration representing the supported classifications of binary media assets
 * managed by the Media Service.
 *
 * <p>Deliberate scope: restricted strictly to custom user-uploaded content
 * (avatars, attachments, custom image/video clips). Never used for proxying or
 * storing native TMDB catalog posters, trailers, or backdrops (ARCHITECTURE.md §3.8).
 */
public enum MediaType {
  /** General image asset (PNG, JPEG, WebP). */
  IMAGE,
  
  /** Video file or clip asset (MP4, WebM). */
  VIDEO,
  
  /** User account profile photo or avatar image (#116). */
  AVATAR,
  
  /** General file attachment on user reviews or social interactions (#117). */
  ATTACHMENT
}
