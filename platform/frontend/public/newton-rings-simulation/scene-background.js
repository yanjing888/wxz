// A quiet, screen-space backdrop. It never receives pointer events or changes
// the instrument's lighting, ray paths, or camera controls.
export function createSceneBackground(host) {
  const canvas = document.createElement("canvas");
  canvas.className = "scene-atmosphere";
  canvas.style.cssText = "position:absolute;inset:0;pointer-events:none";
  canvas.setAttribute("aria-hidden", "true");
  host.prepend(canvas);
  const ctx = canvas.getContext("2d");
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const noise = (n) => { const v = Math.sin(n * 127.1 + 311.7) * 43758.5453; return v - Math.floor(v); };
  const nodes = Array.from({ length: 48 }, (_, i) => ({
    x: noise(i + 1), y: noise(i + 71), phase: noise(i + 141) * Math.PI * 2,
    radius: 0.8 + noise(i + 211) * 1.3,
  }));
  let width = 0, height = 0, ratio = 0, previous = 0, time = 0;
  let enabled = true, visible = true, dirty = true;

  function glow(x, y, radius, color) {
    const gradient = ctx.createRadialGradient(x, y, 0, x, y, radius);
    gradient.addColorStop(0, color);
    gradient.addColorStop(1, "transparent");
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, width, height);
  }
  function draw() {
    ctx.clearRect(0, 0, width, height);
    glow(width * (0.22 + Math.sin(time * 0.13) * 0.05), height * 0.32,
      width * 0.4, "rgba(59, 91, 145, .025)");
    glow(width * (0.8 + Math.cos(time * 0.11) * 0.04), height * 0.42,
      width * 0.35, "rgba(49, 78, 133, .025)");

    // Perspective lines give the empty space a laboratory-floor reference.
    const horizon = height * 0.53, center = width * 0.5;
    ctx.save();
    const floorFade = ctx.createLinearGradient(0, horizon, 0, height);
    floorFade.addColorStop(0, "rgba(145, 178, 224, .025)");
    floorFade.addColorStop(1, "rgba(145, 178, 224, .06)");
    ctx.strokeStyle = floorFade;
    ctx.lineWidth = 0.65;
    ctx.beginPath();
    for (let i = -14; i <= 14; i++) {
      ctx.moveTo(center + i * 12, horizon);
      ctx.lineTo(center + i * width * 0.15, height);
    }
    for (let i = 1; i <= 12; i++) {
      const depth = i / 12;
      const y = horizon + depth * depth * (height - horizon);
      ctx.moveTo(0, y); ctx.lineTo(width, y);
    }
    ctx.stroke();
    const sweep = (time * 0.035 + 0.2) % 1;
    const y = horizon + sweep * sweep * (height - horizon);
    ctx.strokeStyle = `rgba(159, 195, 246, ${Math.sin(sweep * Math.PI) * 0.14})`;
    ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke();
    ctx.restore();

    // Slow drift and breathing points stay behind the physical apparatus.
    const points = nodes.map((n) => ({
      x: n.x * width + Math.sin(time * 0.12 + n.phase) * 18,
      y: (0.06 + n.y * 0.69) * height + Math.cos(time * 0.09 + n.phase) * 14,
      alpha: 0.14 + Math.sin(time * 0.45 + n.phase) * 0.05,
      radius: n.radius,
    }));
    const distanceLimit = Math.min(150, width * 0.13);
    for (let i = 0; i < points.length; i++) {
      const p = points[i];
      for (let j = i + 1; j < points.length; j++) {
        const q = points[j], distance = Math.hypot(p.x - q.x, p.y - q.y);
        if (distance >= distanceLimit) continue;
        ctx.strokeStyle = `rgba(151, 185, 232, ${(1 - distance / distanceLimit) * 0.12})`;
        ctx.lineWidth = 0.7;
        ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(q.x, q.y); ctx.stroke();
      }
      ctx.fillStyle = `rgba(188, 214, 250, ${p.alpha})`;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = `rgba(143, 185, 241, ${p.alpha * 0.08})`;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.radius * 4, 0, Math.PI * 2); ctx.fill();
    }
  }
  return {
    setEnabled(value) { enabled = value; dirty = true; },
    setVisible(value) {
      visible = value;
      canvas.hidden = !value;
      dirty = true;
    },
    update(now, nextWidth, nextHeight) {
      if (!ctx || !visible || document.hidden || nextWidth <= 0 || nextHeight <= 0) { previous = now; return; }
      const nextRatio = Math.min(window.devicePixelRatio || 1, 1.5);
      if (width !== nextWidth || height !== nextHeight || ratio !== nextRatio) {
        width = nextWidth; height = nextHeight; ratio = nextRatio;
        canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        dirty = true;
      }
      if (!dirty && now - previous < 1000 / 24) return;
      const moving = enabled && !reducedMotion.matches;
      const dt = previous ? Math.min((now - previous) / 1000, 0.1) : 0;
      previous = now;
      if (!moving && !dirty) return;
      if (moving) time += dt;
      draw();
      dirty = false;
    },
  };
}
