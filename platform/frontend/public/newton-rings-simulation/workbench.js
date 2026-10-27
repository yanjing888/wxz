import * as T from "./vendor/three.module.js";

function outline(width, depth, radius) {
  const x = width / 2, z = depth / 2;
  const s = new T.Shape();
  s.moveTo(-x + radius, -z);
  s.lineTo(x - radius, -z);
  s.quadraticCurveTo(x, -z, x, -z + radius);
  s.lineTo(x, z - radius);
  s.quadraticCurveTo(x, z, x - radius, z);
  s.lineTo(-x + radius, z);
  s.quadraticCurveTo(-x, z, -x, z - radius);
  s.lineTo(-x, -z + radius);
  s.quadraticCurveTo(-x, -z, -x + radius, -z);
  return s;
}

// A single static texture keeps the fine machining detail inexpensive to render.
function surfaceTexture() {
  const canvas = document.createElement("canvas");
  canvas.width = 1024;
  canvas.height = 768;
  const ctx = canvas.getContext("2d");
  const wash = ctx.createRadialGradient(510, 340, 40, 510, 340, 680);
  wash.addColorStop(0, "#263e4a");
  wash.addColorStop(1, "#101f2b");
  ctx.fillStyle = wash;
  ctx.fillRect(0, 0, 1024, 768);
  ctx.strokeStyle = "rgba(148, 186, 198, 0.045)";
  ctx.lineWidth = 1;
  for (let y = 0; y < 768; y += 3) {
    ctx.beginPath(); ctx.moveTo(0, y + 0.5); ctx.lineTo(1024, y + 0.5); ctx.stroke();
  }
  for (let x = 32; x < 1024; x += 32) {
    for (let y = 32; y < 768; y += 32) {
      ctx.fillStyle = "#0b1821";
      ctx.beginPath(); ctx.arc(x, y, 1.7, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = "rgba(137, 180, 192, 0.2)";
      ctx.fillRect(x - 1, y + 2, 2, 1);
    }
  }
  // Restrained calibration marks sit outside the instrument working area.
  ctx.strokeStyle = "rgba(149, 203, 211, 0.45)";
  for (let x = 64; x <= 960; x += 16) {
    const length = x % 64 === 0 ? 12 : 5;
    ctx.beginPath();
    ctx.moveTo(x, 24); ctx.lineTo(x, 24 + length);
    ctx.moveTo(x, 744); ctx.lineTo(x, 744 - length);
    ctx.stroke();
  }
  const texture = new T.CanvasTexture(canvas);
  texture.colorSpace = T.SRGBColorSpace;
  texture.anisotropy = 4;
  return texture;
}

export function createWorkbench() {
  const group = new T.Group();
  group.name = "optical-workbench";
  group.position.set(0.6, 0, -0.3);
  // Widen the supporting tray for the immersive scene, keeping its height
  // and the instrument geometry unchanged.
  group.scale.set(1.25, 1, 1.2);
  const shape = outline(8, 5.8, 0.28);
  const body = new T.Mesh(
    new T.ExtrudeGeometry(shape, {
      depth: 0.14, bevelEnabled: true, bevelSize: 0.035,
      bevelThickness: 0.025, bevelSegments: 3, steps: 1, curveSegments: 12,
    }),
    new T.MeshStandardMaterial({ color: 0x132631, metalness: 0.6, roughness: 0.42 }),
  );
  body.rotation.x = -Math.PI / 2;
  body.position.y = -0.225;
  body.receiveShadow = true;
  group.add(body);

  const topGeometry = new T.ShapeGeometry(shape, 12);
  const uv = topGeometry.attributes.uv, vertices = topGeometry.attributes.position;
  for (let i = 0; i < uv.count; i++) {
    uv.setXY(i, (vertices.getX(i) + 4) / 8, (vertices.getY(i) + 2.9) / 5.8);
  }
  const top = new T.Mesh(topGeometry, new T.MeshStandardMaterial({
    map: surfaceTexture(), metalness: 0.35, roughness: 0.75,
  }));
  top.rotation.x = -Math.PI / 2;
  top.position.y = -0.058;
  top.receiveShadow = true;
  group.add(top);

  function rim(width, depth, radius, y, color, opacity) {
    const points = outline(width, depth, radius).getPoints(16)
      .map(p => new T.Vector3(p.x, y, -p.y));
    const line = new T.Line(new T.BufferGeometry().setFromPoints(points),
      new T.LineBasicMaterial({ color, transparent: true, opacity, depthWrite: false }));
    group.add(line);
    return points;
  }
  rim(8.04, 5.84, 0.3, -0.2, 0x477581, 0.55);
  rim(7.96, 5.76, 0.26, -0.05, 0x92b5bd, 0.45);
  const rail = rim(7.72, 5.52, 0.24, -0.048, 0x4bb6be, 0.42);
  const path = new T.CurvePath();
  for (let i = 1; i < rail.length; i++) path.add(new T.LineCurve3(rail[i - 1], rail[i]));
  path.getLength();
  const samples = 40;
  const trails = [0, 0.5].map(offset => {
    const geometry = new T.BufferGeometry();
    geometry.setAttribute("position", new T.BufferAttribute(new Float32Array(samples * 6), 3));
    const colors = new Float32Array(samples * 6);
    const indices = [];
    const tint = new T.Color(0x81e8e1);
    for (let i = 0; i < samples; i++) {
      const fade = Math.pow(i / (samples - 1), 1.6);
      for (let side = 0; side < 2; side++) {
        colors.set([tint.r * fade, tint.g * fade, tint.b * fade], (i * 2 + side) * 3);
      }
      if (i < samples - 1) {
        const a = i * 2;
        indices.push(a, a + 1, a + 2, a + 1, a + 3, a + 2);
      }
    }
    geometry.setIndex(indices);
    geometry.setAttribute("color", new T.BufferAttribute(colors, 3));
    const line = new T.Mesh(geometry, new T.MeshBasicMaterial({
      vertexColors: true, transparent: true, opacity: 0.85,
      blending: T.AdditiveBlending, depthWrite: false, toneMapped: false, side: T.DoubleSide,
    }));
    line.frustumCulled = false;
    group.add(line);
    return { line, offset };
  });
  // Decorative surfaces never take focus from an instrument control.
  group.traverse(object => { object.raycast = () => {}; });
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  let previousTime = -Infinity;
  function update(now) {
    if (!group.visible || document.hidden || now - previousTime < 32) return;
    previousTime = now;
    const phase = reducedMotion.matches ? 0.12 : (now / 36000) % 1;
    for (const { line, offset } of trails) {
      const position = line.geometry.attributes.position;
      for (let i = 0; i < samples; i++) {
        const t = (phase + offset + i / (samples - 1) * 0.065) % 1;
        const p = path.getPoint(t), next = path.getPoint((t + 0.0001) % 1);
        const length = Math.hypot(next.x - p.x, next.z - p.z) || 1;
        const nx = (next.z - p.z) / length * 0.022;
        const nz = (p.x - next.x) / length * 0.022;
        position.setXYZ(i * 2, p.x + nx, p.y + 0.004, p.z + nz);
        position.setXYZ(i * 2 + 1, p.x - nx, p.y + 0.004, p.z - nz);
      }
      position.needsUpdate = true;
    }
  }
  update(0);
  return { group, update };
}
