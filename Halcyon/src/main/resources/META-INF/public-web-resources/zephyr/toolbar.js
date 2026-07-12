// toolbar.js
// Initializes the annotation/utility toolbar for a viewer page.
// Modal tools (drawing, edit, label, inspect) register with the ToolManager
// (#30), which owns activation state, mutual exclusion, camera-controls
// gating, cursor and button styling; the remaining entries are panels and
// one-shot actions that manage their own DOM.
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
import { scaleBar } from "./helpers/scaleBar.js";
import { minimap } from "./helpers/minimap.js";
import { compare } from "./helpers/compare.js";
import { featureInfo } from "./helpers/featureInfo.js";
import { installAnnotationKeys } from "./helpers/history.js";
import { ToolManager } from "./toolManager.js";

export function toolbar(scene, camera, renderer, controls, originalZ, config) {
  // Undo/redo + Escape/Delete/PageUp/PageDown bindings (idempotent).
  installAnnotationKeys();

  // One state machine for the mutually-exclusive tools (#30).
  const manager = new ToolManager({ scene, camera, renderer, controls, originalZ });

  const tools = {
    colorPalette: {
      initialize: () => colorPalette(),
      destroy: () => {
        removeElement("colorPalette");
      }
    },
    freeDrawing: {
      initialize: () => enableDrawing(manager)
    },
    rectangle: {
      initialize: () => rectangle(manager, { button: "<i class=\"fa-regular fa-square\"></i>", color: 0x0000ff, select: false })
    },
    rectangleAlt: {
      initialize: () => rectangle(manager, { button: "<i class=\"fas fa-crop-alt\"></i>", color: "#ff7900", select: true })
    },
    ellipse: {
      initialize: () => ellipse(manager)
    },
    polygon: {
      initialize: () => polygon(manager)
    },
    hollowBrush: {
      initialize: () => hollowBrush(manager),
      destroy: () => {
        removeElement("brushSizeSlider");
        removeElement("sliderValueDisplay");
      }
    },
    grid: {
      initialize: () => grid(manager)
    },
    edit: {
      initialize: () => edit(manager)
    },
    label: {
      initialize: () => {
        label(manager, "label");
        label(manager, "area");
      }
    },
    ruler: {
      initialize: () => ruler(manager)
    },
    featureInfo: {
      initialize: () => featureInfo(manager),
      destroy: () => {
        removeElement("featureInfoPopup");
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
        removeElement("brightnessContainer");
        removeElement("contrastContainer");
        removeElement("brightContrastReset");
      }
    },
    getImageName: {
      initialize: () => getImageName(scene),
      destroy: () => {
        removeElement("imageNameDiv");
      }
    },
    scaleBar: {
      initialize: () => scaleBar(camera, renderer, controls),
      destroy: () => {
        removeElement("scaleBar");
      }
    },
    minimap: {
      initialize: () => minimap(camera, renderer, controls),
      destroy: () => {
        removeElement("minimap");
      }
    },
    compare: {
      initialize: () => compare(camera, renderer, controls),
      destroy: () => {
        cleanupButton("compare"); // stop clipping tiles before the UI goes
        removeElement("compare");
        removeElement("comparePanel");
        removeElement("compareDivider");
      }
    }
  };

  const toolbarManager = {
    enabled: config.toolbarEnabled,
    tools: tools,
    manager: manager,
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
      // The manager deactivates the live tool (removing its canvas listeners
      // and re-enabling the camera controls), runs every tool's onDestroy
      // and removes their buttons; the per-tool destroy entries below cover
      // the panels/actions that own their own DOM.
      manager.destroy();
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

// Tools with live scene/listener state hang a teardown on their button
// (button.__cleanup); run it before the DOM node disappears.
function cleanupButton(name) {
  const element = document.getElementById(name);
  if (element && typeof element.__cleanup === 'function') {
    element.__cleanup();
  }
}
