/**
 * The 3D model of an item, built as the game builds it: a sprite of 16 x 16 pixels becomes a
 * plate one pixel thick, each pixel a little block, with its edges where it meets the empty
 * pixels around it (ItemModelGenerator of Minecraft). A block is a cube with its textures, a head
 * the cube of a skin's head.
 */
import type * as Three from 'three';
import type { ItemArt } from '@/lib/mc';

type ThreeModule = typeof Three;

const loadImage = (src: string): Promise<HTMLImageElement> =>
  new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`cannot load ${src}`));
    image.src = src;
  });

function pixels(image: HTMLImageElement): ImageData {
  const canvas = document.createElement('canvas');
  canvas.width = image.naturalWidth;
  canvas.height = image.naturalHeight;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) {
    throw new Error('no 2D canvas');
  }
  context.drawImage(image, 0, 0);
  return context.getImageData(0, 0, canvas.width, canvas.height);
}

function texture(THREE: ThreeModule, image: HTMLImageElement): Three.Texture {
  const map = new THREE.Texture(image);
  map.magFilter = THREE.NearestFilter;
  map.minFilter = THREE.NearestFilter;
  map.generateMipmaps = false;
  map.colorSpace = THREE.SRGBColorSpace;
  map.needsUpdate = true;
  return map;
}

/** The plate of a flat item, one unit wide, centred on the origin. */
function sprite(THREE: ThreeModule, data: ImageData): Three.Mesh {
  const { width, height } = data;
  const unit = 1 / width;
  const half = unit / 2; // the plate is one pixel thick
  const positions: number[] = [];
  const normals: number[] = [];
  const colours: number[] = [];
  const colour = new THREE.Color();
  const opaque = (x: number, y: number) =>
    x >= 0 && y >= 0 && x < width && y < height && (data.data[(y * width + x) * 4 + 3] ?? 0) > 127;

  const quad = (corners: number[][], normal: number[]) => {
    for (const index of [0, 1, 2, 0, 2, 3]) {
      positions.push(...(corners[index] as number[]));
      normals.push(...normal);
      colours.push(colour.r, colour.g, colour.b);
    }
  };

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (!opaque(x, y)) {
        continue;
      }
      const i = (y * width + x) * 4;
      colour.setRGB((data.data[i] ?? 0) / 255, (data.data[i + 1] ?? 0) / 255, (data.data[i + 2] ?? 0) / 255, THREE.SRGBColorSpace);
      const x0 = x * unit - 0.5;
      const x1 = x0 + unit;
      const y1 = 0.5 - y * unit;
      const y0 = y1 - unit;
      quad([[x0, y0, half], [x1, y0, half], [x1, y1, half], [x0, y1, half]], [0, 0, 1]);
      quad([[x1, y0, -half], [x0, y0, -half], [x0, y1, -half], [x1, y1, -half]], [0, 0, -1]);
      if (!opaque(x - 1, y)) quad([[x0, y0, -half], [x0, y0, half], [x0, y1, half], [x0, y1, -half]], [-1, 0, 0]);
      if (!opaque(x + 1, y)) quad([[x1, y0, half], [x1, y0, -half], [x1, y1, -half], [x1, y1, half]], [1, 0, 0]);
      if (!opaque(x, y - 1)) quad([[x0, y1, half], [x1, y1, half], [x1, y1, -half], [x0, y1, -half]], [0, 1, 0]);
      if (!opaque(x, y + 1)) quad([[x0, y0, -half], [x1, y0, -half], [x1, y0, half], [x0, y0, half]], [0, -1, 0]);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3));
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colours, 3));
  return new THREE.Mesh(geometry, new THREE.MeshLambertMaterial({ vertexColors: true }));
}

/** The faces of a head in a 64 x 16 strip: [column, row] of 8 pixels, in the order of a box. */
const HEAD_FACES: [number, number][] = [
  [2, 1], // +x: the left of the head
  [0, 1], // -x: its right
  [1, 0], // +y: the top
  [2, 0], // -y: the bottom
  [1, 1], // +z: the face
  [3, 1], // -z: the back
];

function head(THREE: ThreeModule, image: HTMLImageElement): Three.Group {
  const group = new THREE.Group();
  for (const [layer, size] of [[0, 0.62], [4, 0.62 * 1.125]] as const) {
    const materials = HEAD_FACES.map(([column, row]) => {
      const map = texture(THREE, image);
      map.repeat.set(1 / 8, 1 / 2);
      map.offset.set((column + layer) / 8, row === 0 ? 0.5 : 0);
      return new THREE.MeshLambertMaterial({ map, transparent: layer > 0, alphaTest: 0.5 });
    });
    group.add(new THREE.Mesh(new THREE.BoxGeometry(size, size, size), materials));
  }
  return group;
}

function block(THREE: ThreeModule, top: HTMLImageElement, side: HTMLImageElement): Three.Mesh {
  const sideMaterial = new THREE.MeshLambertMaterial({ map: texture(THREE, side) });
  const topMaterial = new THREE.MeshLambertMaterial({ map: texture(THREE, top) });
  const size = 0.62;
  return new THREE.Mesh(new THREE.BoxGeometry(size, size, size), [
    sideMaterial,
    sideMaterial,
    topMaterial,
    topMaterial,
    sideMaterial,
    sideMaterial,
  ]);
}

export async function buildItem(THREE: ThreeModule, art: ItemArt): Promise<Three.Object3D> {
  if (art.kind === 'item') {
    return sprite(THREE, pixels(await loadImage(art.src)));
  }
  if (art.kind === 'head') {
    return head(THREE, await loadImage(art.src));
  }
  const [top, side] = await Promise.all([loadImage(art.top), loadImage(art.side)]);
  return block(THREE, top, side);
}

/** Frees what the model holds on the graphics card. */
export function disposeItem(object: Three.Object3D): void {
  object.traverse((child) => {
    const mesh = child as Three.Mesh;
    if (mesh.geometry) {
      mesh.geometry.dispose();
    }
    const materials = Array.isArray(mesh.material) ? mesh.material : mesh.material ? [mesh.material] : [];
    for (const material of materials) {
      (material as Three.MeshLambertMaterial).map?.dispose();
      material.dispose();
    }
  });
}
