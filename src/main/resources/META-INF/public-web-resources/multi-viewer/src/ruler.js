/**
 * A measuring tool.  Measure in microns.
 *
 * @param {object} btnRuler - Button that activates the ruler
 * @param {object} viewer - OpenSeadragon.viewer
 * @param {object} overlay - Canvas on which to draw the measurement
 */
const ruler = (btnRuler, viewer, overlay) => {
  let line;
  let isDown;
  let zoom;
  let mode = 'x';
  let fText;
  let fStart = { x: 0, y: 0 };
  let fEnd = { x: 0, y: 0 };
  let oStart;
  let oEnd;

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
  fabric.Object.prototype.transparentCorners = false;

  function clear() {
    fStart.x = 0.0;
    fEnd.x = 0.0;
    fStart.y = 0.0;
    fEnd.y = 0.0;
    canvas.remove(...canvas.getItemsByName('ruler'));
  }

  function mouseDownHandler(o) {
    clear();
    zoom = viewer.viewport.getZoom(true);
    if (mode === 'draw') {
      setOsdTracking(viewer, false);
      isDown = true;

      let webPoint = new OpenSeadragon.Point(o.e.clientX, o.e.clientY);
      try {
        let viewportPoint = viewer.viewport.pointFromPixel(webPoint);
        oStart = viewer.world.getItemAt(0).viewportToImageCoordinates(viewportPoint);
      } catch (e) {
        console.error(e.message);
      }

      let pointer = canvas.getPointer(o.e);
      let points = [pointer.x, pointer.y, pointer.x, pointer.y];
      fStart.x = pointer.x;
      fStart.y = pointer.y;
      line = new fabric.Line(points, {
        strokeWidth: adjustor().lineWidth, // adjust stroke width on zoom
        stroke: lineColor,
        originX: 'center',
        originY: 'center',
        selectable: false,
        evented: false,
        name: 'ruler'
      });
      canvas.add(line);
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
    canvas.remove(fText); // remove text element before re-adding it

    fText = new fabric.Text(text, {
      left: x,
      top: y,
      fill: fontColor,
      fontFamily: "effra,Verdana,Tahoma,'DejaVu Sans',sans-serif",
      fontSize: adjustor().fontSize,
      textBackgroundColor: bgColor,
      selectable: false,
      evented: false,
      name: 'ruler'
    });
    canvas.add(fText);
  }

  function mouseMoveHandler(o) {
    if (!isDown) return;

    let webPoint = new OpenSeadragon.Point(o.e.clientX, o.e.clientY);
    let viewportPoint = viewer.viewport.pointFromPixel(webPoint);
    oEnd = viewer.world.getItemAt(0).viewportToImageCoordinates(viewportPoint);

    let w = difference(oStart.x, oEnd.x);
    let h = difference(oStart.y, oEnd.y);
    let hypot = getHypotenuseLength(w, h, MICRONS_PER_PIX);
    let t = valueWithUnit(hypot);

    let pointer = canvas.getPointer(o.e);
    line.set({ x2: pointer.x, y2: pointer.y });
    fEnd.x = pointer.x;
    fEnd.y = pointer.y;

    if (mode === 'draw') {
      // Show info while drawing line
      drawText(fEnd.x, fEnd.y, t);
    }
    canvas.renderAll();
  }

  function mouseUpHandler(o) {
    line.setCoords();
    isDown = false;

    // Make sure user actually drew a line
    if (!(fStart.x === fEnd.x || fStart.y === fEnd.y || fEnd.x === 0)) {
      console.log(`%clength: ${fText.text}`, 'color: #ccff00;');
      let pointer = canvas.getPointer(o.e);
      drawText(pointer.x, pointer.y, fText.text);
      canvas.renderAll();
    }
  }

  btnRuler.addEventListener('click', () => {
    if (mode === 'draw') {
      // Turn off
      canvas.remove(...canvas.getItemsByName('ruler'));
      mode = 'x';
      canvas.off('mouse:down', mouseDownHandler);
      canvas.off('mouse:move', mouseMoveHandler);
      canvas.off('mouse:up', mouseUpHandler);
    } else {
      // Turn on
      mode = 'draw';
      canvas.on('mouse:down', mouseDownHandler);
      canvas.on('mouse:move', mouseMoveHandler);
      canvas.on('mouse:up', mouseUpHandler);
    }
    toggleButton(btnRuler, 'btnOn', 'annotationBtn');
  });
};
