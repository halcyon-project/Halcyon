// Define global variables
window.cancerColor = '';
window.cancerType = '';

import { enableDrawing } from './annotations/free-drawing.js';
import { rectangle } from './annotations/rectangle.js';
import { ellipse } from './annotations/ellipse.js';
import { polygon } from './annotations/polygon.js';
import { ruler } from './helpers/ruler.js';
import { grid } from './annotations/grid.js';
import { hollowBrush } from "./annotations/hollow-brush.js";
import { edit } from "./helpers/edit.js";
import { label } from "./helpers/labels.js";
import { crosshairs } from "./helpers/crosshairs.js";
import { save } from "./helpers/save.js";
import { zoomControl, lockRotate, resetCamera } from "./helpers/zoomControl.js";
import { screenCapture } from "./helpers/elements.js";
// import { shading } from "./helpers/shading.js";
import { colorPalette } from "./helpers/colorPalette.js";
import { brightContrast} from "./helpers/brightContrast.js";

export function toolbar(scene, camera, renderer, controls, originalZ) {
  colorPalette();
  enableDrawing(scene, camera, renderer, controls);
  rectangle(scene, camera, renderer, controls, {
    button: "<i class=\"fa-regular fa-square\"></i>",
    color: 0x0000ff,
    select: false
  });
  ellipse(scene, camera, renderer, controls);
  polygon(scene, camera, renderer, controls);
  hollowBrush(scene, camera, renderer, controls);
  edit(scene, camera, renderer, controls, originalZ);
  label(scene, camera, renderer, controls, originalZ);
  // shading(scene);
  grid(scene, camera, renderer, controls);
  ruler(scene, camera, renderer, controls);
  screenCapture(renderer);
  crosshairs(scene, camera);
  rectangle(scene, camera, renderer, controls, {
    button: "<i class=\"fa fa-bar-chart\"></i>",
    color: 0xff7900,
    select: true
  });
  save(scene);
  lockRotate(controls);
  resetCamera(controls);
  zoomControl(camera, controls, originalZ);
  brightContrast(scene);
}
