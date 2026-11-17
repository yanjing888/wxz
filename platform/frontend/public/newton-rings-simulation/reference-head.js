import * as T from './vendor/three.module.js';

// Photo reference: a LOW horizontal circular seat, with a tapered casting rising
// into a smaller 45-degree neck. A single diagonally cut cylinder is not this part.
export function createPrismHousingGeometry() {
  const vertices = [], indices = [];
  const segments = 96, rows = 12;
  const radius = 0.392, neckRadius = 0.213;
  for (let row = 0; row <= rows; row++) {
    const t = row / rows;
    for (let i = 0; i <= segments; i++) {
      const angle = i * Math.PI * 2 / segments;
      const c = Math.cos(angle), s = Math.sin(angle);
      const foot = [radius * c, 0.065, radius * s];
      // The small upper opening is perpendicular to the inclined optical axis.
      const neck = [-0.21 + neckRadius * Math.SQRT1_2 * c,
        0.23 + neckRadius * Math.SQRT1_2 * c, neckRadius * s];
      // Slightly bowed cast-metal transition, rather than a bulbous dome.
      const bow = 0.018 * Math.sin(Math.PI * t);
      vertices.push(foot[0] * (1-t) + neck[0] * t,
        foot[1] * (1-t) + neck[1] * t + bow,
        foot[2] * (1-t) + neck[2] * t);
    }
  }
  for (let row = 0; row < rows; row++) {
    for (let i = 0; i < segments; i++) {
      const a = row * (segments+1) + i, b = a + segments+1;
      indices.push(a, b, a+1, b, b+1, a+1);
    }
  }
  const geometry = new T.BufferGeometry();
  geometry.setAttribute('position', new T.Float32BufferAttribute(vertices, 3));
  geometry.setIndex(indices);
  geometry.computeVertexNormals();
  geometry.computeBoundingBox();
  return geometry;
}

// Flat cheeks visible on either side of the inclined neck in the close-up.
export function createPrismCheekGeometry(side = 1) {
  const z = side * 0.177;
  const geometry = new T.BufferGeometry();
  const a = [-0.335, 0.075, z], b = [-0.245, 0.318, z * 0.86], c = [0.245, 0.075, z];
  const points = side > 0 ? [...a, ...c, ...b] : [...a, ...b, ...c];
  geometry.setAttribute('position', new T.Float32BufferAttribute(points, 3));
  geometry.computeVertexNormals();
  return geometry;
}
