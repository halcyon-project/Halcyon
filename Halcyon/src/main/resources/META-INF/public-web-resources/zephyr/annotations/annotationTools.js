import { enableDrawing } from './drawingModule.js';
import { shapes } from './shapes.js';
import { ruler } from './ruler.js';
import { grid } from './grid.js';

export function annotationTools(scene, camera, renderer, controls) {
  // Enable drawing on the scene
  enableDrawing(scene, camera, renderer, controls);
  shapes(scene, camera, renderer, controls);
  ruler(scene, camera, renderer, controls);
  grid(scene);
}
