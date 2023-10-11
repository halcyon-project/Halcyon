/** @file commonFunctions.js - Contains utility functions */

/**
 * Change the way the image is displayed, based on user input.
 *
 * @param {Array} layers - Layers (images) to be displayed in viewer
 * @param {object} viewer - OpenSeadragon viewer
 * @param {object} [range] - For inside/outside; e.g. { "min": 70, "max": 170, "type": "inside" }
 * @param {object} [thresh] - Class thresholding; e.g. { "val": 128, "rgba": [ 255, 255, 0, 255 ], "classId": 1 }
 */
function setFilter(layers, viewer, range, thresh) {
  if (viewer.world) {
    // let start = performance.now();
    // console.log(setFilter.caller);

    const itemCount = viewer.world.getItemCount();
    let filterOpts = [];

    // One does not color just the affected layer; you have to do all of them.
    for (let i = 1; i < itemCount; i++) {
      const tiledImage = viewer.world.getItemAt(i);

      if (!isEmpty(range)) {
        // Use range values
        let rgba;
        if (range.type === 'inside') {
          rgba = [0, 255, 255, 255]; // #00ffff (Cyan)
        } else {
          rgba = [74, 0, 180, 255]; // #4a00b4 (Purple Heart)
        }
        filterOpts.push({
          items: tiledImage,
          processors: OpenSeadragon.Filters.PROBABILITY(range, rgba)
        });
      } else if (STATE.outline) {
        // Outline in blue.  Color can not have green in it.
        filterOpts.push({
          items: tiledImage,
          processors: OpenSeadragon.Filters.OUTLINE([0, 0, 255, 255]),
        });
      } else if (STATE.renderType === 'byProbability') {
        // Use color spectrum
        filterOpts.push({
          items: tiledImage,
          processors: OpenSeadragon.Filters.COLORLEVELS(layers[i].colorscheme.colorspectrum),
        });
      } else if (STATE.renderType === 'byClass' || STATE.renderType === 'byHeatmap') {
        let processor;
        if (thresh) {
          // Use threshold
          processor = OpenSeadragon.Filters.THRESHOLDING(thresh);
        } else {
          // Use color scheme
          processor = OpenSeadragon.Filters.COLORLEVELS(layers[i].colorscheme.colors);
        }
        filterOpts.push({
          items: tiledImage,
          processors: processor
        });
      } else if (STATE.renderType === 'byThreshold') {
        filterOpts.push({
          items: tiledImage,
          processors: OpenSeadragon.Filters.THRESHOLDING(thresh)
        });
      }
    }

    if (!isEmpty(filterOpts)) {
      try {
        // Set all layers at once (required)
        viewer.setFilterOptions({
          filters: filterOpts,
          loadMode: 'sync'
        });
      } catch (e) {
        console.error(e.message);
      }
    }

    // let end = performance.now();
    // console.log("start:", start, "end:", end);
    // console.log(`exec: ${end - start}`);

  } else {
    console.warn('No viewer.world');
  }
}

/**
 * Freeze/unfreeze viewer to allow for drawing.
 *
 * @param {object} viewer - OpenSeadragon.Viewer
 * @param {boolean} enable - toggle (enable/disable)
 */
function setOsdTracking(viewer, enable) {
  viewer.setMouseNavEnabled(enable);
  viewer.outerTracker.setTracking(enable);
  viewer.gestureSettingsMouse.clickToZoom = enable;
}

/**
 * Toggle between class names.
 *
 * Highlight/un-highlight button to indicate "on" or "off".
 *
 * @param {object} element - HTML element
 * @param {string} class0 - class0
 * @param {string} class1 - class1
 */
function toggleButton(element, class0, class1) {
  element.classList.toggle(class0);
  element.classList.toggle(class1);
}

/**
 * Test for null or undefined.
 *
 * @param obj
 * @return {boolean}
 */
function isRealValue(obj) {
  return obj !== null && obj !== undefined;
}

/**
 * Check to see if an Array, a string, or an object is empty.
 *
 * @param {Array|object|string} value
 * @return {boolean}
 */
const isEmpty = value => {
  const isEmptyObject = a => {
    if (typeof a.length === 'undefined') {
      // it's an Object, not an Array
      const hasNonempty = Object.keys(a).some(element => {
        return !isEmpty(a[element]);
      });
      return hasNonempty ? false : isEmptyObject(Object.keys(a));
    }

    return !a.some(element => {
      // check if array is really not empty as JS thinks
      return !isEmpty(element); // at least one element should be non-empty
    });
  };
  return (
    value === false
    || typeof value === 'undefined'
    || value === null
    || (typeof value === 'object' && isEmptyObject(value))
  );
};

/**
 * Message dialog.
 *
 * @param messageObject
 * @return {boolean}
 */
function alertMessage(messageObject) {
  window.alert(messageObject);
  return true;
}

/**
 * Randomize.
 *
 * @param {number} minm
 * @param {number} maxm
 * @return {number}
 */
function getRandomInt(minm, maxm) {
  return Math.floor(Math.random() * (maxm - minm + 1)) + minm;
}

/**
 * Generate random ID.
 *
 * @param {number} length
 * @param {string} prefix
 * @return {string}
 */
function createId(length, prefix) {
  let result = '';
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const charactersLength = characters.length;
  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength));
  }
  if (prefix) {
    result = prefix + result;
  }
  return result;
}

/**
 * Generate random ID 2.
 *
 * @return {string}
 */
function createId2() {
  return `_${Math.random().toString(36).substring(2, 13)}`;
}

/**
 * Get items by name, when the names are non-unique.
 *
 * @param {string} name
 * @return {Array}
 */
fabric.Canvas.prototype.getItemsByName = function(name) {
  const objectList = [];
  const objects = this.getObjects();

  for (let i = 0, len = this.size(); i < len; i++) {
    if (objects[i].name && objects[i].name === name) {
      objectList.push(objects[i]);
    }
  }

  return objectList;
};

/**
 * Element creation abstraction
 *
 * @param {string} tagName
 * @param {object} properties
 * @param {Array} children
 * @return {object}
 */
const e = (tagName, properties = {}, children = []) => {
  // Create the element
  const element = document.createElement(tagName);

  // Apply properties
  Object.keys(properties).forEach(property => {
    element.setAttribute(property, properties[property]);
  });

  // Append children
  children.forEach(c => {
    if (!c) return;
    const node = typeof c === 'string' ? document.createTextNode(c) : c;
    element.appendChild(node);
  });

  return element;
};

/**
 * rgb/a to array
 *
 * @param input
 * @return {*}
 */
function colorToArray(input) {
  const arrStr = input.replace(/[a-z%\s()]/g, '').split(',');
  return arrStr.map(i => Number(i));
}

/**
 * Scale rgb colors to percentage
 *
 * @param num
 * @return {number}
 */
const scaleToPct = num => {
  return (num / 255) * 100;
};

/**
 * num to rgb
 *
 * @param num
 * @return {number}
 */
const scaleToRgb = num => {
  return (num * 255) / 100;
};

/**
 * Render Types
 *
 * @type {string[]}
 */
// const RENDER_TYPES = ['byProbability', 'byClass', 'byHeatmap', 'byThreshold'];
const RENDER_TYPES = ['byProbability', 'byClass', 'byHeatmap'];

/**
 * State
 *
 * @type {{attenuate: boolean, outline: boolean, renderType: string}}
 */
const STATE = {
  attenuate: false,
  outline: false,
  renderType: RENDER_TYPES[0]
};

/**
 * Stringify shortcut
 * @param param
 * @return {string}
 */
function stringy(param) {
  return JSON.stringify(param);
}

/**
 * Setting values in storage
 * @param canvas
 * @param options
 */
function populateStorage(canvas, options) {
  localStorage.setItem('theme', document.body.className);
  const myObject = canvas.toJSON(['name', 'tag']);
  localStorage.setItem('canvas', stringy(myObject));
  localStorage.setItem('state', stringy(STATE));
  localStorage.setItem('options', stringy(options));
  // console.log("saved", window.localStorage);
}

/**
 * Save user settings and markup:
 *
 * <ul>
 *  <li> CSS theme (dark, light)
 *  <li> Canvas objects (polygons, annotations, measurement, etc.)
 *  <li> App state (attenuate, outline, renderType)
 *  <li> Options (toolbarOn, paintbrushColor)
 * </ul>
 *
 * @param {object} canvas - Our osd fabric.js canvas object
 * @param {object} options
 * @param {string} options.paintbrushColor - example: "#0ff"
 * @param {boolean} options.toolbarOn - example: true
 */
function saveSettings(canvas, options) {
  // For now, set values in localStorage
  populateStorage(canvas, options);
  const jsonObject = {
    theme: document.body.className,
    canvas: canvas.toJSON(['name', 'tag']),
    state: STATE,
    options,
  };
  console.log('saved', jsonObject);
  // console.log('canvas', jsonObject.canvas.objects);
  // console.log('stringify', stringy(jsonObject));
}

/**
 * Location of application images and osd images.
 *
 * The following setup is for running this app on our Apache Wicket implementation.
 *
 * @type {{appImages: string, osdImages: string}}
 */
let CONFIG = {
  osdImages: '/multi-viewer/vendor/openseadragon/images/',
  appImages: '/multi-viewer/images/'
};

/** Configuration below is for working *locally*. */
// let CONFIG = {
//   osdImages: 'vendor/openseadragon/images/',
//   appImages: 'images/'
// }

/**
 * Max (as in color range 0 - 255)
 *
 * @type {number}
 */
const MAX = 255;

/**
 * Microns Per Pixel (default=0.25; actual value set later)
 *
 * @type {number}
 */
let MICRONS_PER_PIX = 0.25;
