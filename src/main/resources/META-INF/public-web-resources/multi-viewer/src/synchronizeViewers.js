/**
 * Synchronize pan & zoom on every viewer in the given array.
 *
 * @param {Array} multiViewerArray - Array of MultiViewer objects
 */
const synchronizeViewers = function(multiViewerArray) {
  const isGood = checkData(multiViewerArray);

  if (isGood) {
    this.SYNCED_IMAGE_VIEWERS = [];
    this.activeViewerId = null;
    this.numViewers = multiViewerArray.length;

    multiViewerArray.forEach(function(imageViewer) {
      const currentViewer = imageViewer.getViewer();

      setPanZoomCurrent(currentViewer, handler);

      // set this up while we're here
      mapMarker(currentViewer, this.SYNCED_IMAGE_VIEWERS);

      function handler() {
        if (!isActive(currentViewer.id)) {
          return;
        }

        setPanZoomOthers(imageViewer);

        resetFlag();
      }

      this.SYNCED_IMAGE_VIEWERS.push(imageViewer);
    });
  }
};

function setPanZoomCurrent(currentViewer, handler) {
  currentViewer.addHandler('pan', handler);
  currentViewer.addHandler('zoom', handler);
}

function isActive(currentId) {
  init(currentId);
  return currentId === this.activeViewerId;
}

function init(currentId) {
  if (!isRealValue(this.activeViewerId)) {
    this.activeViewerId = currentId;
  }
}

function isPanOn(imageViewer) {
  const checkboxes = imageViewer.getPanZoom();

  if (typeof checkboxes.checkPan.checked !== 'undefined') {
    return checkboxes.checkPan.checked; // user decision
  }
  // If 1 div; then, nothing to synchronize.
  return this.numViewers !== 1;
}

function isZoomOn(imageViewer) {
  const checkboxes = imageViewer.getPanZoom();

  if (typeof checkboxes.checkZoom.checked !== 'undefined') {
    return checkboxes.checkZoom.checked; // user decision
  }
  // If 1 div; then, nothing to synchronize.
  return this.numViewers !== 1;
}

function setPanZoomOthers(imageViewer) {
  const currentViewer = imageViewer.getViewer();

  this.SYNCED_IMAGE_VIEWERS.forEach(syncedObject => {
    const syncedViewer = syncedObject.getViewer();

    if (syncedViewer.id === currentViewer.id) {
      return;
    }

    if (isPanOn(syncedObject) && isPanOn(imageViewer)) {
      syncedViewer.viewport.panTo(currentViewer.viewport.getCenter(false), false);
    }

    if (isZoomOn(syncedObject) && isZoomOn(imageViewer)) {
      syncedViewer.viewport.zoomTo(currentViewer.viewport.getZoom(false));
    }
  });
}

function resetFlag() {
  this.activeViewerId = null;
}

function checkData(multiViewerArray) {
  if (isEmpty(multiViewerArray)) {
    console.error('synchronizeViewers.js: Expected input argument, but received none.');
    return false;
  }

  if (!(multiViewerArray[0] instanceof Object)) {
    console.error('synchronizeViewers.js: Array elements should be MultiViewer objects.');
    return false;
  }

  return true;
}
