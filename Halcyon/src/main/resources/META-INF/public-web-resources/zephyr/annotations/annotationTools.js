import { enableDrawing } from './drawingModule.js';
import { rectangle } from './rectangle.js';
import { ellipse } from './ellipse.js';
import { polygon } from './polygon.js';
import { ruler } from './ruler.js';
import { grid } from './grid.js';
import { hollowBrush } from "./hollow-brush.js";
import { save } from "./save.js";

export function annotationTools(scene, camera, renderer, controls) {
  // Enable drawing on the scene
  enableDrawing(scene, camera, renderer, controls);
  rectangle(scene, camera, renderer, controls);
  ellipse(scene, camera, renderer, controls);
  polygon(scene, camera, renderer, controls);
  ruler(scene, camera, renderer, controls);
  hollowBrush(scene, camera, renderer, controls);
  grid(scene, camera, renderer);
  save(scene);
}
