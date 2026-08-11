import React, { useRef, useEffect } from 'react';

/**
 * Animated SMPTE 35mm Film Leader & Projector Beam Canvas.
 *
 * <p>Renders a retro cinema countdown radar, floating projector dust particles,
 * and vintage 35mm celluloid film grain in real time.
 *
 * @param {Object} props
 * @param {number} props.secondsRemaining - Seconds remaining in the countdown
 * @param {number} props.progressPercentage - Progress percentage (0 - 100)
 */
export function CinemaLeaderCanvas({ secondsRemaining, progressPercentage }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const ctx = canvas.getContext('2d');
    if (!ctx) return undefined;

    let animationFrameId;
    canvas.width = canvas.offsetWidth || 400;
    canvas.height = canvas.offsetHeight || 300;
    let { width, height } = canvas;

    const handleResize = () => {
      if (!canvas) return;
      canvas.width = canvas.offsetWidth || 400;
      canvas.height = canvas.offsetHeight || 300;
      ({ width, height } = canvas);
    };

    window.addEventListener('resize', handleResize);

    // Generate 35 floating dust particles in the projector beam
    const particles = Array.from({ length: 35 }, () => ({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.4,
      vy: -0.2 - Math.random() * 0.5,
      radius: 0.8 + Math.random() * 1.8,
      alpha: 0.1 + Math.random() * 0.4,
      pulseSpeed: 0.02 + Math.random() * 0.03,
    }));

    let radarAngle = 0;
    let frameCount = 0;

    const render = () => {
      frameCount += 1;
      radarAngle += 0.035; // ~2 seconds per full revolution

      ctx.clearRect(0, 0, width, height);

      const centerX = width / 2;
      const centerY = height / 2;
      const radius = Math.min(width, height) * 0.36;

      // 1. Volumetric Projector Cone Beam (Luminous Gradient)
      const beamGrad = ctx.createRadialGradient(
        centerX,
        0,
        10,
        centerX,
        centerY,
        Math.max(width, height),
      );
      beamGrad.addColorStop(0, 'rgba(245, 197, 24, 0.18)');
      beamGrad.addColorStop(0.3, 'rgba(229, 9, 20, 0.08)');
      beamGrad.addColorStop(0.7, 'rgba(10, 10, 20, 0.02)');
      beamGrad.addColorStop(1, 'rgba(0, 0, 0, 0)');

      ctx.fillStyle = beamGrad;
      ctx.beginPath();
      ctx.moveTo(centerX, 0);
      ctx.lineTo(width * 0.95, height);
      ctx.lineTo(width * 0.05, height);
      ctx.closePath();
      ctx.fill();

      // 2. Floating Projector Dust Particles
      particles.forEach((p) => {
        p.x += p.vx;
        p.y += p.vy;
        p.alpha += Math.sin(frameCount * p.pulseSpeed) * 0.01;

        // Wrap around
        if (p.y < 0) {
          p.y = height;
          p.x = Math.random() * width;
        }
        if (p.x < 0) p.x = width;
        if (p.x > width) p.x = 0;

        ctx.beginPath();
        ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 235, 180, ${Math.max(0.05, Math.min(0.6, p.alpha))})`;
        ctx.shadowBlur = 4;
        ctx.shadowColor = '#f5c518';
        ctx.fill();
        ctx.shadowBlur = 0;
      });

      // 3. SMPTE Universal Film Leader Rings
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';

      // Outer Target Ring
      ctx.beginPath();
      ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
      ctx.stroke();

      // Inner Precision Ring
      ctx.beginPath();
      ctx.arc(centerX, centerY, radius * 0.75, 0, Math.PI * 2);
      ctx.stroke();

      // Innermost Hub Ring
      ctx.beginPath();
      ctx.arc(centerX, centerY, radius * 0.35, 0, Math.PI * 2);
      ctx.stroke();

      // 4. Crosshairs & 35mm Frame Calibration Ticks
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = 'rgba(245, 197, 24, 0.3)';

      // Horizontal crosshair
      ctx.beginPath();
      ctx.moveTo(centerX - radius * 1.2, centerY);
      ctx.lineTo(centerX + radius * 1.2, centerY);
      ctx.stroke();

      // Vertical crosshair
      ctx.beginPath();
      ctx.moveTo(centerX, centerY - radius * 1.2);
      ctx.lineTo(centerX, centerY + radius * 1.2);
      ctx.stroke();

      ctx.setLineDash([]); // Reset dash

      // 5. Sweeping Radar Line & Sector Fill (Like iconic classic film leader)
      ctx.save();
      ctx.beginPath();
      ctx.moveTo(centerX, centerY);
      ctx.arc(
        centerX,
        centerY,
        radius,
        radarAngle - 0.4,
        radarAngle,
        false,
      );
      ctx.closePath();

      const sweepGrad = ctx.createRadialGradient(
        centerX,
        centerY,
        0,
        centerX,
        centerY,
        radius,
      );
      sweepGrad.addColorStop(0, 'rgba(229, 9, 20, 0.4)');
      sweepGrad.addColorStop(1, 'rgba(245, 197, 24, 0.05)');
      ctx.fillStyle = sweepGrad;
      ctx.fill();

      // Radar line stroke
      ctx.beginPath();
      ctx.moveTo(centerX, centerY);
      ctx.lineTo(
        centerX + Math.cos(radarAngle) * radius,
        centerY + Math.sin(radarAngle) * radius,
      );
      ctx.strokeStyle = '#e50914';
      ctx.lineWidth = 2.5;
      ctx.shadowBlur = 8;
      ctx.shadowColor = '#e50914';
      ctx.stroke();
      ctx.restore();

      // 6. Real-time Circular Progress Indicator Arc
      const startAngle = -Math.PI / 2;
      const progressAngle = startAngle + (Math.PI * 2 * (progressPercentage / 100));

      ctx.beginPath();
      ctx.arc(centerX, centerY, radius + 8, startAngle, progressAngle);
      ctx.strokeStyle = '#f5c518';
      ctx.lineWidth = 3;
      ctx.shadowBlur = 10;
      ctx.shadowColor = '#f5c518';
      ctx.stroke();
      ctx.shadowBlur = 0;

      // 7. Subtle 35mm Celluloid Optical Scratches / Grain (Procedural)
      if (Math.random() > 0.6) {
        const scratchX = Math.random() * width;
        ctx.strokeStyle = `rgba(255, 255, 255, ${0.05 + Math.random() * 0.1})`;
        ctx.lineWidth = 0.5;
        ctx.beginPath();
        ctx.moveTo(scratchX, 0);
        ctx.lineTo(scratchX + (Math.random() - 0.5) * 6, height);
        ctx.stroke();
      }

      animationFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      window.removeEventListener('resize', handleResize);
      cancelAnimationFrame(animationFrameId);
    };
  }, [progressPercentage, secondsRemaining]);

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
        zIndex: 0,
      }}
    />
  );
}

export default CinemaLeaderCanvas;
