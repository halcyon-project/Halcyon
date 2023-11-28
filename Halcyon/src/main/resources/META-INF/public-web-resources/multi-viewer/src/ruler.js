/**
 * A measuring tool.  Measure in microns.
 *
 * @param {object} btnRuler - Button that activates the ruler
 * @param {object} viewer - OpenSeadragon.viewer
 * @param {object} overlay - Canvas on which to draw the measurement
 */
const ruler = (btnRuler, viewer, overlay) => {
  let fabLine;
  let isDown;
  let zoom;
  let mode = 'x';
  let fabText;
  let fabStart = { x: 0, y: 0 };
  let fabEnd = { x: 0, y: 0 };
  let osdStart = { x: 0, y: 0 };
  let osdEnd = { x: 0, y: 0 };

  // Define original or base font size and rectangle dimensions
  const baseFontSize = 15;
  const baseStrokeWidth = 2;

  let bgColor, fontColor, lineColor;
  // lineColor = '#ccff00'; // neon yellow
  // lineColor = '#39ff14'; // neon green
  // lineColor = '#b3f836'; // in-between
  // fontColor = '#000';
  // bgColor = 'rgba(255,255,255,0.5)';

  bgColor = '#009933';
  fontColor = '#fff';
  lineColor = '#00cc01';

  let canvas = overlay.fabricCanvas();

  function clear() {
    fabStart.x = 0.0;
    fabEnd.x = 0.0;
    fabStart.y = 0.0;
    fabEnd.y = 0.0;
    canvas.remove(...canvas.getItemsByName('ruler'));
  }

  function mouseDownHandler(o) {
    clear();
    zoom = viewer.viewport.getZoom(true);
    if (mode === 'draw') {
      setOsdTracking(viewer, false);
      isDown = true;

      try {
        if (!o || !o.e) throw new Error('Event object or client coordinates are missing');
        let webPoint = new OpenSeadragon.Point(o.e.clientX, o.e.clientY);

        if (!viewer || !viewer.viewport) throw new Error('Viewer or viewport is not initialized');
        let viewportPoint = viewer.viewport.pointFromPixel(webPoint);

        let item = viewer.world.getItemAt(0);
        if (!item) throw new Error('No item found at index 0 in the viewer world');
        osdStart = item.viewportToImageCoordinates(viewportPoint);
      } catch (e) {
        console.error(e.message);
      }

      let pointer = canvas.getPointer(o.e);
      let points = [pointer.x, pointer.y, pointer.x, pointer.y];
      fabStart.x = pointer.x;
      fabStart.y = pointer.y;
      fabLine = new fabric.Line(points, {
        strokeWidth: adjustor().lineWidth, // adjust stroke width on zoom
        stroke: lineColor,
        originX: 'center',
        originY: 'center',
        selectable: false,
        evented: false,
        name: 'ruler'
      });
      canvas.add(fabLine);
    } else {
      setOsdTracking(viewer, true); // keep image from panning/zooming as you draw line
      canvas.forEachObject(obj => {
        obj.setCoords(); // update coordinates
      });
    }
  }

  function difference(a, b) {
    return Math.abs(a - b);
  }

  function getHypotenuseLength(a, b, mpp) {
    if (!mpp || typeof mpp !== 'number' || mpp <= 0) {
      console.error("Invalid MICRONS_PER_PIX value:", mpp);
      return 0;
    }
    return Math.sqrt(a * a * mpp * mpp + b * b * mpp * mpp);
  }

  function valueWithUnit(value) {
    if (value < 0.000001) {
      // 1 µ = 1e+9 fm
      return `${(value * 1000000000).toFixed(3)} fm`;
    }
    if (value < 0.001) {
      // 1 µ = 1e+6 pm
      return `${(value * 1000000).toFixed(3)} pm`;
    }
    if (value < 1) {
      // 1 µ = 1000 nm
      return `${(value * 1000).toFixed(3)} nm`;
    }
    if (value >= 1000) {
      // 1 µ = 0.001 mm
      return `${(value / 1000).toFixed(3)} mm`;
    }
    // 1 µ
    return `${value.toFixed(3)} \u00B5m`;
  }

  function adjustor() {
    let currentZoom = viewer.viewport.getZoom(true); // Get the current zoom level from OpenSeadragon.
    let scaleFactor = 1 / currentZoom; // Calculate a scaling factor based on the zoom level.
    // Adjust original dimensions with scaleFactor
    let adjustedFontSize = baseFontSize * scaleFactor;
    let adjustedStrokeWidth = baseStrokeWidth * scaleFactor;
    return { scaleFactor: scaleFactor, fontSize: adjustedFontSize, lineWidth: adjustedStrokeWidth };
  }

  function drawText(x, y, text) {
    canvas.remove(fabText); // remove text element before re-adding it

    fabText = new fabric.Text(text, {
      left: x - 0.5,
      top: y - 0.5,
      originX: 'left',
      originY: 'top',
      fill: fontColor,
      fontFamily: "Arial,Helvetica,sans-serif",
      fontSize: adjustor().fontSize,
      textBackgroundColor: bgColor,
      selectable: false,
      evented: false,
      name: 'ruler'
    });
    canvas.add(fabText);
  }

  function mouseMoveHandler(o) {
    if (!isDown) return;

    let webPoint = new OpenSeadragon.Point(o.e.clientX, o.e.clientY);
    let viewportPoint = viewer.viewport.pointFromPixel(webPoint);
    osdEnd = viewer.world.getItemAt(0).viewportToImageCoordinates(viewportPoint);

    let w = difference(osdStart.x, osdEnd.x);
    let h = difference(osdStart.y, osdEnd.y);
    let hypot = getHypotenuseLength(w, h, MICRONS_PER_PIX);
    let t = valueWithUnit(hypot);

    let pointer = canvas.getPointer(o.e);
    fabLine.set({ x2: pointer.x, y2: pointer.y });
    fabEnd.x = pointer.x;
    fabEnd.y = pointer.y;

    if (mode === 'draw') {
      // Show info while drawing line
      drawText(fabEnd.x, fabEnd.y, t);
    }
    canvas.renderAll();
  }

  function mouseUpHandler(o) {
    isDown = false;
    // canvas.forEachObject(function(object) {
    //   console.log("object", object);
    //   object.setCoords(); // update coordinates
    //   object.set('selectable', true);
    // });
    fabLine.setCoords();
    fabText.setCoords();

    // Make sure user actually drew a line
    if (!(fabStart.x === fabEnd.x || fabStart.y === fabEnd.y || fabEnd.x === 0)) {
      console.log(`%clength: ${fabText.text}`, 'color: #ccff00;');
      let pointer = canvas.getPointer(o.e);
      drawText(pointer.x, pointer.y, fabText.text);
      canvas.renderAll();
    }
  }

  btnRuler.addEventListener('click', () => {
    const isDrawMode = mode === 'draw';

    mode = isDrawMode ? 'x' : 'draw';

    if (isDrawMode) {
      canvas.remove(...canvas.getItemsByName('ruler'));
      canvas.off('mouse:down', mouseDownHandler);
      canvas.off('mouse:move', mouseMoveHandler);
      canvas.off('mouse:up', mouseUpHandler);
    } else {
      canvas.on('mouse:down', mouseDownHandler);
      canvas.on('mouse:move', mouseMoveHandler);
      canvas.on('mouse:up', mouseUpHandler);
    }
    toggleButton(btnRuler, 'btnOn', 'annotationBtn');
  });
};
