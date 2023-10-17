/**
 * Create the fabric.js overlay and pass it to the markup tools.
 *
 * @param {object} vInfo - Info specific to 'this' viewer
 * @param {object} options - Filters, paintbrush, etc.
 * @param {object} viewer - OpenSeadragon viewer
 */
const markupTools = (vInfo, options, viewer) => {
  const overlay = viewer.fabricjsOverlay({ scale: 1, static: false });
  const idx = vInfo.idx;

  drawPolygon(vInfo, viewer, overlay);

  editPolygon(document.getElementById(`btnEdit${idx}`), overlay);

  gridOverlay(
    document.getElementById(`btnGrid${idx}`),
    document.getElementById(`btnGridMarker${idx}`),
    overlay,
  );

  ruler(document.getElementById(`btnRuler${idx}`), viewer, overlay);

  blender(document.getElementById(`btnBlender${idx}`), viewer, false); // Set to true if you want to show descriptions

  const canvas = overlay.fabricCanvas();
  canvas.on('after:render', () => {
    canvas.calcOffset();
  });

  const btnSave = document.getElementById(`btnSave${vInfo.idx}`);
  btnSave.addEventListener('click', () => {
    saveSettings(canvas, options, vInfo.STATE);
  });
};
