// This script initializes, configures, and manages a toolbar of various annotation and helper tools that interact 
// with a rendering scene, as well as allows user to enable/disable tools and remove the elements associated with the 
// tools from the webpage.
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
import { fetchAnnotations } from "./helpers/fetchAnnotations.js";
import { zoomControl, lockRotation, resetCamera } from "./helpers/zoomControl.js";
import { screenCapture } from "./helpers/elements.js";
import { colorPalette } from "./helpers/colorPalette.js";
import { brightContrast } from "./helpers/brightContrast.js";
import { getImageName } from "./helpers/getImageName.js";

export function toolbar(scene, camera, renderer, controls, originalZ, config) {
  const tools = {
    colorPalette: {
      initialize: () => colorPalette(),
      destroy: () => {
        removeElement("colorPalette");
      }
    },
    freeDrawing: {
      initialize: () => enableDrawing(scene, camera, renderer, controls),
      destroy: () => {
        removeElement("freeDrawing");
      }
    },
    rectangle: {
      initialize: () => rectangle(scene, camera, renderer, controls, { button: "<i class=\"fa-regular fa-square\"></i>", color: 0x0000ff, select: false }),
      destroy: () => {
        removeElement("rectangle");
      }
    },
    rectangleAlt: {
      initialize: () => rectangle(scene, camera, renderer, controls, { button: "<i class=\"fas fa-crop-alt\"></i>", color: "#ff7900", select: true }),
      destroy: () => {
        removeElement("selection");
      }
    },
    ellipse: {
      initialize: () => ellipse(scene, camera, renderer, controls),
      destroy: () => {
        removeElement("ellipse");
      }
    },
    polygon: {
      initialize: () => polygon(scene, camera, renderer, controls),
      destroy: () => {
        removeElement("polygon");
      }
    },
    hollowBrush: {
      initialize: () => hollowBrush(scene, camera, renderer, controls),
      destroy: () => {
        removeElement("hollowBrush");
      }
    },
    grid: {
      initialize: () => grid(scene, camera, renderer, controls),
      destroy: () => {
        removeElement("grid");
      }
    },
    edit: {
      initialize: () => edit(scene, camera, renderer, controls, originalZ),
      destroy: () => {
        removeElement("edit");
      }
    },
    label: {
      initialize: () => {
        label(scene, camera, renderer, controls, originalZ, "label");
        label(scene, camera, renderer, controls, originalZ, "area");
      },
      destroy: () => {
        removeElement("label");
        removeElement("area");
      }
    },
    ruler: {
      initialize: () => ruler(scene, camera, renderer, controls),
      destroy: () => {
        removeElement("ruler");
      }
    },
    screenCapture: {
      initialize: () => screenCapture(renderer),
      destroy: () => {
        removeElement("screenCapture");
      }
    },
    crosshairs: {
      initialize: () => crosshairs(scene, camera),
      destroy: () => {
        removeElement("crosshairs");
      }
    },
    save: {
      initialize: () => save(scene),
      destroy: () => {
        removeElement("save");
      }
    },
    fetchAnnotations: {
      initialize: () => fetchAnnotations(scene),
      destroy: () => {
        removeElement("fetchAnnotations");
      }
    },
    zoomControl: {
      initialize: () => {
        lockRotation(controls);
        resetCamera(controls);
        zoomControl(camera, controls, originalZ);
      },
      destroy: () => {
        removeElement("zoomControl");
        removeElement("lockRotation");
        removeElement("resetCamera");
      }
    },
    brightContrast: {
      initialize: () => brightContrast(scene),
      destroy: () => {
        removeElement("brightnessSlider");
        removeElement("contrastSlider");
        removeElement("resetButton");
      }
    },
    getImageName: {
      initialize: () => getImageName(scene),
      destroy: () => {
        removeElement("imageNameDiv");
      }
    }
  };

  const toolbarManager = {
    enabled: config.toolbarEnabled,
    tools: tools,
    initialize() {
      if (this.enabled) {
        for (let tool in this.tools) {
          if (config.tools[tool] && config.tools[tool].enabled) {
            this.tools[tool].initialize();
          }
        }
      }
    },
    applyConfig(config) {
      this.enabled = config.toolbarEnabled;
      if (this.enabled) {
        this.initialize();
      } else {
        this.clearTools();
      }
    },
    clearTools() {
      for (let tool in this.tools) {
        if (typeof this.tools[tool].destroy === "function") {
          this.tools[tool].destroy();
        }
      }
    }
  };

  toolbarManager.applyConfig(config);

  return toolbarManager;
}

function removeElement(name) {
  const element = document.getElementById(name);
  if (element) {
    element.remove();
  }
}
