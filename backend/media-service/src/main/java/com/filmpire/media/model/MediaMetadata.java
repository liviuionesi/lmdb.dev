package com.filmpire.media.model;

/**
 * Immutable domain record representing specific technical properties and dimensions
 * of a stored user-uploaded binary media asset.
 *
 * @param width Horizontal display resolution in pixels (for image/video assets).
 * @param height Vertical display resolution in pixels (for image/video assets).
 * @param duration Total playback duration in seconds (for video audio stream assets).
 * @param codec Encoding format identification string (e.g., H.264, VP9, AAC).
 * @param bitrate Data transfer consumption rate in bits per second.
 */
public record MediaMetadata(
    Integer width,
    Integer height,
    Integer duration,
    String codec,
    Long bitrate
) {
}
