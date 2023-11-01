/**
 * For the spot that was right-clicked in viewer A, place a marker
 * at that location on all viewers.
 *
 * @param {object} osdViewer - The OpenSeadragon viewer that has the focus
 * @param {object} imageViewerArray - Array of ImageViewers
 */
const mapMarker = (osdViewer, imageViewerArray) => {
  overrideRightClickMenu(osdViewer.element);

  handleMarkerDisplay(osdViewer, imageViewerArray);

  handleButtonShowHide();
};

function overrideRightClickMenu(viewerDiv) {
  viewerDiv.addEventListener('contextmenu', mouseEvent => {
    mouseEvent.preventDefault();
  });
}

function handleMarkerDisplay(osdViewer, imageViewerArray) {
  osdViewer.addHandler('canvas-nonprimary-press', osdEvent => {
    if (isRightClick(osdEvent)) {
      const clickPosition = osdEvent.position;
      const clickPositionViewport = osdViewer.viewport.pointFromPixel(clickPosition);

      const buttons = document.querySelectorAll('#btnMapMarker');
      buttons.forEach(item => {
        item.style.display = 'block';
      });
      displayMapMarker(clickPositionViewport, osdViewer, imageViewerArray);
    }
  });
}

function isRightClick(evt) {
  return evt.button === 2;
}

function displayMapMarker(point, osdViewer, imageViewerArray) {
  imageViewerArray.forEach(item => {
    item.getViewer().addOverlay({
      element: createLink(),
      location: point,
      placement: 'BOTTOM',
      checkResize: false
    });
  });
}

function createLink() {
  const link = e('a', { href: '#', id: 'pin', class: 'fas fa-map-marker pointer' });
  link.style = 'text-decoration: none; font-size: 22px; color: red;';
  link.dataset.href = '#';
  return link;
}

function handleButtonShowHide() {
  const buttons = document.querySelectorAll('#btnMapMarker');
  buttons.forEach(elem => {
    let markersHaveBeenDrawn = false;
    let style;
    let html;
    elem.addEventListener('click', function() {
      if (markersHaveBeenDrawn) {
        style = 'block';
        html = '<i class="fas fa-map-marker"></i> Hide markers';
      } else {
        style = 'none';
        html = '<i class="fas fa-map-marker"></i> Show markers';
      }
      this.innerHTML = html;
      document.querySelectorAll('#pin').forEach(thing => {
        thing.style.display = style;
      });
      markersHaveBeenDrawn = !markersHaveBeenDrawn;
    });
  });
}
