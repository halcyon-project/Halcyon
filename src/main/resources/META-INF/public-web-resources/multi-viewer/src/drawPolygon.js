/**
 * Allow user to draw a polygon on the image.
 *
 * @param {object} viewerInfo - Info specific to 'this' viewer
 * @param {object} viewer - OSD viewer object
 * @param {object} overlay - Canvas on which to draw the polygon
 */
const drawPolygon = (viewerInfo, viewer, overlay) => {
  let idx = viewerInfo.idx;
  let btnDraw = document.getElementById(`btnDraw${idx}`);
  let mark = document.getElementById(`mark${idx}`);
  let canvas = overlay.fabricCanvas();
  let tag;

  let paintBrush = canvas.freeDrawingBrush = new fabric.PencilBrush(canvas);
  paintBrush.decimate = 12;
  paintBrush.color = mark.innerHTML;

  canvas.on('mouse:over', evt => {
    if (evt.target !== null) {
      fillPolygon(evt, canvas, true);
    }
  });

  canvas.on('mouse:out', evt => {
    if (evt.target !== null) {
      fillPolygon(evt, canvas, false);
    }
  });

  canvas.on('mouse:up', (evt) => {
    // annotate(evt); // TODO: wip
    drawingOff(canvas, viewer);
  });

  canvas.on('path:created', opts => {
    tag = createId2();
    pathCreatedHandler(opts, btnDraw, canvas, paintBrush, viewer);
  });

  btnDraw.addEventListener('click', function () {
    toggleButton(this, 'btnOn', 'annotationBtn');
    if (canvas.isDrawingMode) {
      // Drawing off
      drawingOff(canvas, viewer);
    } else {
      // Drawing on
      canvas.isDrawingMode = true;
      canvas.on('mouse:down', () => {
        setGestureSettings(canvas, viewer);
      });
      paintBrush.color = mark.innerHTML;
      paintBrush.width = 10 / viewer.viewport.getZoom(true);
      setOsdTracking(viewer, false);
    }
  });

  function annotate(evt) {
    if (canvas.isDrawingMode) {
      let target = evt.currentTarget;

      // FABRIC TEXTBOX
      let text = new fabric.Textbox('Annotate...', {
        width: 250,
        cursorColor: 'blue',
        top: target.top + target.height + 10,
        left: target.left + target.width + 10,
        fontSize: 20,
        editable: true,
        tag: tag
      });
      canvas.add(text);

      /*
      // Try to re-create/simplify annotorious-style div for annotation
      let left, top;
      top = target.top + target.height + 25;
      left = target.left + target.width + 25;
      let myDiv = `<div class="r6o-editor r6o-arrow-top r6o-arrow-left" style="transform: translate(0px); top: ${top}px; left: ${left}px; opacity: 1;">
      <div class="r6o-arrow"></div><!-- ARROW -->
      <div class="r6o-editor-inner">
        <div class="r6o-widget comment">
          <textarea class="r6o-editable-text" placeholder="Add a comment..." disabled rows="1" style="overflow: hidden; overflow-wrap: break-word; height: 35px;"></textarea>
          <div class="r6o-icon r6o-arrow-down">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 940" width="12">
              <metadata>IcoFont Icons</metadata>
              <title>simple-down</title>
              <glyph glyph-name="simple-down" unicode="\uEAB2" horiz-advx="1000"></glyph>
              <path fill="currentColor" d="M200 392.6l300 300 300-300-85.10000000000002-85.10000000000002-214.89999999999998 214.79999999999995-214.89999999999998-214.89999999999998-85.10000000000002 85.20000000000005z"></path>
            </svg>
          </div>
        </div><!-- END comment -->
        <div class="r6o-widget comment editable">
          <textarea class="r6o-editable-text" placeholder="Add a reply..." rows="1" style="overflow: hidden; overflow-wrap: break-word; height: 35px;"></textarea>
        </div><!-- END reply -->
        <div class="r6o-widget r6o-tag">
          <ul class="r6o-taglist">
            <!-- existing tags go here. -->
            <li></li>
          </ul><!-- END taglist -->
          <div class="r6o-autocomplete">
            <div><input placeholder="Add tag..."></div>
            <ul><!-- tags go here --></ul>
          </div><!-- END add tag -->
        </div><!-- END tag section -->
        <div class="r6o-footer r6o-draggable">
          <button class="r6o-btn left delete-annotation" title="Delete">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 448 512" width="12">
              <path fill="currentColor" d="M268 416h24a12 12 0 0 0 12-12V188a12 12 0 0 0-12-12h-24a12 12 0 0 0-12 12v216a12 12 0 0 0 12 12zM432 80h-82.41l-34-56.7A48 48 0 0 0 274.41 0H173.59a48 48 0 0 0-41.16 23.3L98.41 80H16A16 16 0 0 0 0 96v16a16 16 0 0 0 16 16h16v336a48 48 0 0 0 48 48h288a48 48 0 0 0 48-48V128h16a16 16 0 0 0 16-16V96a16 16 0 0 0-16-16zM171.84 50.91A6 6 0 0 1 177 48h94a6 6 0 0 1 5.15 2.91L293.61 80H154.39zM368 464H80V128h288zm-212-48h24a12 12 0 0 0 12-12V188a12 12 0 0 0-12-12h-24a12 12 0 0 0-12 12v216a12 12 0 0 0 12 12z"></path>
            </svg>
          </button><!-- DELETE button -->
          <button class="r6o-btn outline">Cancel</button><!-- CANCEL button -->
          <button class="r6o-btn">OK</button><!-- OK button -->
        </div><!-- END footer -->
      </div><!-- END editor-inner -->
    </div><!-- END editor -->`;
      try {
        const myDiv1 = e('div');
        myDiv1.style.left = `${left}px`;
        myDiv1.style.top = `${top}px`;
        myDiv1.innerHTML = myDiv;
        document.body.appendChild(myDiv1);

      } catch (e) {
        console.log(`%c${e.message}`, "color: #ff00cc;");
      }
      */
    }
  }

  function drawingOff(canvas, viewer) {
    canvas.isDrawingMode = false;
    canvas.off('mouse:down', () => {
      setGestureSettings(canvas, viewer);
    });
    setOsdTracking(viewer, true);
  }

  function pathCreatedHandler(options, button, canvas, paintBrush, viewer) {
    convertPathToPolygon(options.path, canvas, paintBrush);
    setupDeleteButton(options.path);
    toggleButton(button, 'annotationBtn', 'btnOn');
    canvas.off('path:created', () => {
      pathCreatedHandler(options, button, canvas, paintBrush, viewer);
    });
  }

  function setGestureSettings(canvas, viewer) {
    viewer.gestureSettingsMouse.clickToZoom = !canvas.getActiveObject();
  }

  function setupDeleteButton(obj) {
    obj.lockMovementX = true;
    obj.lockMovementY = true;
    let deleteIcon = "data:image/svg+xml,%3C%3Fxml version='1.0' encoding='utf-8'%3F%3E%3C!DOCTYPE svg PUBLIC '-//W3C//DTD SVG 1.1//EN' 'http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd'%3E%3Csvg version='1.1' id='Ebene_1' xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' x='0px' y='0px' width='595.275px' height='595.275px' viewBox='200 215 230 470' xml:space='preserve'%3E%3Ccircle style='fill:%23F44336;' cx='299.76' cy='439.067' r='218.516'/%3E%3Cg%3E%3Crect x='267.162' y='307.978' transform='matrix(0.7071 -0.7071 0.7071 0.7071 -222.6202 340.6915)' style='fill:white;' width='65.545' height='262.18'/%3E%3Crect x='266.988' y='308.153' transform='matrix(0.7071 0.7071 -0.7071 0.7071 398.3889 -83.3116)' style='fill:white;' width='65.544' height='262.179'/%3E%3C/g%3E%3C/svg%3E";

    let deleteImg = document.createElement('img');
    deleteImg.src = deleteIcon;

    function renderIcon(icon) {
      return function renderIcon(ctx, left, top, styleOverride, fabricObject) {
        let size = this.cornerSize;
        ctx.save();
        ctx.translate(left, top);
        ctx.rotate(fabric.util.degreesToRadians(fabricObject.angle));
        ctx.drawImage(icon, -size / 2, -size / 2, size, size);
        ctx.restore();
      };
    }

    fabric.Object.prototype.controls.deleteControl = new fabric.Control({
      x: 0.5,
      y: -0.5,
      offsetX: 15,
      offsetY: -15,
      cursorStyle: 'pointer',
      mouseUpHandler: deleteObject,
      render: renderIcon(deleteImg),
      cornerSize: 24
    });

    function deleteObject(mouseEvent, transform) {
      let target = transform.target;
      try {
        let canvas = target.canvas;
        canvas.remove(target);
        canvas.requestRenderAll();
      } catch (e) {
        console.error(`%c${e.message}`, 'font-size: larger;');
      }
    }
  }

  function convertPathToPolygon(pathObject, canvas, paintBrush) {
    let _points0 = pathObject.path.map(item => ({
      x: item[1],
      y: item[2]
    }));

    let poly = new fabric.Polygon(_points0, {
      left: pathObject.left,
      top: pathObject.top,
      fill: '',
      strokeWidth: paintBrush.width,
      stroke: paintBrush.color,
      scaleX: pathObject.scaleX,
      scaleY: pathObject.scaleY,
      objectCaching: false,
      transparentCorners: false,
      cornerColor: 'rgba(0, 0, 255, 0.5)',
      cornerStyle: 'square',
      tag: tag
    });
    canvas.add(poly);
    poly.setControlVisible('tr', false);
    canvas.setActiveObject(poly);
    canvas.remove(pathObject);
  }

  function fillPolygon(pointerEvent, canvas, fill) {
    let obj = pointerEvent.target;

    if (isRealValue(obj) && obj.type === 'polygon') {
      // polygon hover
      if (fill) {
        obj.set({
          fill: obj.stroke,
          opacity: 0.5
        });
        // displayInfo(obj, canvas);
      } else {
        obj.set({
          fill: ''
        });
        // canvas.remove(infoText);
      }
      canvas.renderAll();
    }
  }

  // function displayInfo(obj, canvas) {
  //   // Display some kind of information. TBA.
  //   // Right now this displays what type of object it is. (Polygon, obviously.)
  //   let type = obj.type;
  //   let left = obj.left;
  //   let top = obj.top;
  //
  //   let infoText = new fabric.Text(type, {
  //     fontSize: 18,
  //     fontFamily: 'Courier',
  //     backgroundColor: 'rgba(102, 102, 102, 0.7)',
  //     stroke: 'rgba(255, 255, 255, 1)',
  //     fill: 'rgba(255, 255, 255, 1)',
  //     left, // pointer.x,
  //     top, // pointer.y
  //   });
  //   canvas.add(infoText);
  // }
};
