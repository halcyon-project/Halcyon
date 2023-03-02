/**
 * Wrapper component around OpenSeadragon viewer.
 * Set up OSD viewer to allow for multiple viewer control.
 * @extends ImageViewer
 */
class MultiViewer extends ImageViewer {
  /**
   * @param {object} viewerInfo - Info specific to 'this' viewer
   * @param {number} numViewers - Total number of viewers.
   * @param {object} options - Filters, paintbrush, etc.
   */
  constructor(viewerInfo, numViewers, options) {
    super(viewerInfo);

    if (typeof options === 'undefined') {
      options = {};
    }

    this.checkboxes = {
      checkPan: true,
      checkZoom: true
    };

    if (numViewers > 1) {
      this.checkboxes.checkPan = document.getElementById(`chkPan${viewerInfo.idx}`);
      this.checkboxes.checkZoom = document.getElementById(`chkZoom${viewerInfo.idx}`);
    }

    if (options.toolbarOn) {
      markupTools(viewerInfo, options, super.getViewer());
    }

    layerUI(
      document.getElementById(`layersAndColors${viewerInfo.idx}`),
      viewerInfo.layers,
      super.getViewer(),
    );
  }

  /**
   * @return {OpenSeadragon.Viewer}
   */
  getViewer() {
    return super.getViewer();
  }

  /**
   * @return {object} {checkPan: boolean, checkZoom: boolean}
   */
  getPanZoom() {
    return this.checkboxes;
  }
}
