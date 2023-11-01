/**
 * Wrapper component around OpenSeadragon viewer.
 * Set up 1 basic OSD viewer.
 */
class ImageViewer {
  /**
   * @param {object} viewerInfo - Info specific to 'this' viewer
   */
  constructor(viewerInfo) {
    const layers = viewerInfo.layers;

    // Array of tileSources for the viewer
    const tileSources = [];
    for (let i = 0; i < layers.length; i++) {
      const layer = layers[i];
      tileSources.push({ tileSource: layer.location, opacity: layer.opacity, x: 0, y: 0 });
    }
    // console.log('tileSources', stringy(ts));

    // SET UP VIEWER
    let viewer;
    try {
      viewer = OpenSeadragon({
        id: viewerInfo.osdId,
        prefixUrl: CONFIG.osdImages,
        tileSources,
        crossOriginPolicy: 'Anonymous',
        blendTime: 0,
        minZoomImageRatio: 1,
        maxZoomPixelRatio: 1, // when the user zooms all the way in they are at 100%
      });
    } catch (e) {
      console.error(e.message);
    }

    // 2.7.7
    // let anno = OpenSeadragon.Annotorious(viewer, {
    //   locale: "auto",
    //   drawOnSingleClick: true,
    //   allowEmpty: true
    // });
    // anno.setAuthInfo({
    //   id: "http://www.example.com/tdiprima",
    //   displayName: "tdiprima"
    // });
    // anno.setDrawingTool("rect");
    // anno.setDrawingEnabled(true);

    // 0.6.4
    // const button = document.getElementById(`btnAnnotate${viewerInfo.idx}`);
    // button.addEventListener("click", function() {
    //   anno.activateSelector();
    //   return false;
    // });
    // make annotatable by Annotorious library
    // anno.makeAnnotatable(viewer);

    let drawer;
    function addInfo(item) {
      try {
        const itemIndex = viewer.world.getIndexOfItem(item);
        const source = viewer.world.getItemAt(itemIndex).source;

        if (typeof source.prefLabel !== 'undefined') layers[itemIndex].prefLabel = source.prefLabel;
        if (typeof source.resolutionUnit !== 'undefined') layers[itemIndex].resolutionUnit = source.resolutionUnit;
        if (typeof source.xResolution !== 'undefined') layers[itemIndex].xResolution = source.xResolution;
      } catch (e) {
        console.log(`%c${e.message}`, 'color: #ff6a5a;');
      }
    }

    // When an item is added to the World, grab the info
    viewer.world.addHandler('add-item', ({ item }) => {
      addInfo(item);
    });

    function _parseHash() {
      const params = {};
      const hash = window.location.hash.replace(/^#/, '');
      if (hash) {
        const parts = hash.split('&');
        parts.forEach(part => {
          const subparts = part.split('=');
          const key = subparts[0];
          const value = parseFloat(subparts[1]);
          if (!key || isNaN(value)) {
            console.error('bad hash param', part);
          } else {
            params[key] = value;
          }
        });
      }

      return params;
    }

    function _useParams(params) {
      // console.log("params", typeof params, params);
      const zoom = viewer.viewport.getZoom();
      const pan = viewer.viewport.getCenter();

      // In Chrome, these fire when you pan/zoom AND tab-switch to something else (like your IDE)
      if (params.zoom !== undefined && params.zoom !== zoom) {
        viewer.viewport.zoomTo(params.zoom, null, true);
      }

      if (params.x !== undefined && params.y !== undefined && (params.x !== pan.x || params.y !== pan.y)) {
        const point = new OpenSeadragon.Point(params.x, params.y);
        viewer.viewport.panTo(point, true);
      }
    }

    // Image has been downloaded and can be modified before being drawn to the canvas.
    viewer.addOnceHandler('tile-loaded', () => {
      drawer = viewer.drawer;
      drawer.imageSmoothingEnabled = false;
      drawer._imageSmoothingEnabled = false;
      // console.log('drawer', drawer)

      if (window.location.hash) {
        const params = _parseHash();
        _useParams(params);
      }
      addCustomButtons();
      setFilter(layers, viewer);
      getInfoForScalebar();
    });

    viewer.addOnceHandler("open", e => {
      // SETUP ZOOM TO MAGNIFICATION - 10x, 20x, etc.
      let minViewportZoom = viewer.viewport.getMinZoom();
      // let minImgZoom = viewer.viewport.viewportToImageZoom(minViewportZoom);
      let tiledImage = viewer.world.getItemAt(0);
      let minImgZoom = tiledImage.viewportToImageZoom(minViewportZoom);

      let arr = [1, 0.5, 0.25];
      let n = 1;
      let imgZoom = [];
      do {
        imgZoom = [...imgZoom, ...arr.map(e => e / n)];
        n *= 10;
      } while (imgZoom[imgZoom.length - 1] > minImgZoom);

      while (imgZoom[imgZoom.length - 1] < minImgZoom) {
        imgZoom.pop();
      }
      imgZoom.push(minImgZoom);
      imgZoom.sort((a, b) => {
        return a - b;
      });

      let htm = "";
      let magContent = document.querySelector(".mag-content");
      if (magContent) {
        for (let i = 0; i < imgZoom.length; i++) {
          htm += `<a href="#" data-value="${imgZoom[i]}">${Number((imgZoom[i] * 40).toFixed(3))}x</a>`;
        }
        magContent.innerHTML = htm;

        for (let el of magContent.children) {
          el.addEventListener("click", () => {
            let attr = el.getAttribute("data-value");
            let imageZoom = parseFloat(attr);
            viewer.viewport.zoomTo(viewer.world.getItemAt(0).imageToViewportZoom(imageZoom));
          });
        }
      }
    });

    // BOOKMARK URL with ZOOM and X,Y
    document.getElementById(`btnShare${viewerInfo.idx}`).addEventListener('click', () => {
      const zoom = viewer.viewport.getZoom();
      const pan = viewer.viewport.getCenter();
      const url = `${location.origin}${location.pathname}#zoom=${zoom}&x=${pan.x}&y=${pan.y}`;
      const I = viewer.world.getItemAt(0);
      // console.log('image coords', I.viewportToImageCoordinates(pan));
      // console.log('url', url);

      prompt('Share this link:', url);
    });

    function timeStamp() {
      const dateString = new Date().toISOString();
      const a = dateString.slice(0, 10);
      let b = dateString.slice(10);
      b = b
        .replaceAll(':', '-')
        .replace('T', '')
        .slice(0, 8);
      return `${a}_${b}`;
    }

    /**
     * Download image snapshot
     */
    document.getElementById(`btnCam${viewerInfo.idx}`).addEventListener('click', () => {
      const parent = document.getElementById(viewerInfo.osdId);
      const children = parent.querySelectorAll('[id^="osd-overlaycanvas"]');

      for (const canvasEl of children) {
        const id = canvasEl.id;
        const num = parseInt(id.slice(-1));
        if (num % 2 === 0) {
          const ctx = viewer.drawer.context;
          ctx.drawImage(canvasEl, 0, 0);
          const osdImg = viewer.drawer.canvas.toDataURL('image/png');
          const downloadLink = document.createElement('a');
          downloadLink.href = osdImg;
          downloadLink.download = `img_${timeStamp()}`;
          downloadLink.click();
          break;
        }
      }
    });

    /**
     * Custom OpenSeadragon Buttons
     * Zoom in 100%
     * Zoom out 100%
     */
    function addCustomButtons() {
      // Zoom all the way in
      const zinButton = new OpenSeadragon.Button({
        tooltip: 'Zoom to 100%',
        srcRest: `${CONFIG.appImages}zin_rest.png`,
        srcGroup: `${CONFIG.appImages}zin_grouphover.png`,
        srcHover: `${CONFIG.appImages}zin_hover.png`,
        srcDown: `${CONFIG.appImages}zin_pressed.png`,
        onClick() {
          viewer.viewport.zoomTo(viewer.viewport.getMaxZoom());
          // viewer.viewport.zoomTo(viewer.world.getItemAt(0).imageToViewportZoom(1.0));
        }
      });

      // Zoom all the way out
      const zoutButton = new OpenSeadragon.Button({
        tooltip: 'Zoom to 0%',
        srcRest: `${CONFIG.appImages}zout_rest.png`,
        srcGroup: `${CONFIG.appImages}zout_grouphover.png`,
        srcHover: `${CONFIG.appImages}zout_hover.png`,
        srcDown: `${CONFIG.appImages}zout_pressed.png`,
        onClick() {
          viewer.viewport.goHome(true);
          // viewer.viewport.zoomTo(viewer.viewport.getHomeZoom());
        }
      });
      viewer.addControl(zinButton.element, { anchor: OpenSeadragon.ControlAnchor.TOP_LEFT });
      viewer.addControl(zoutButton.element, { anchor: OpenSeadragon.ControlAnchor.TOP_LEFT });
    }

    /**
     * Set up scale bar
     * @param ppm
     */
    const setScaleBar = ppm => {
      // console.log("ppm", typeof ppm, ppm);
      viewer.scalebar({
        type: OpenSeadragon.ScalebarType.MICROSCOPY,
        pixelsPerMeter: ppm,
        location: OpenSeadragon.ScalebarLocation.BOTTOM_LEFT,
        xOffset: 5,
        yOffset: 10,
        stayInsideImage: true,
        color: 'rgb(150, 150, 150)',
        fontColor: 'rgb(100, 100, 100)',
        backgroundColor: 'rgba(255, 255, 255, 0.5)',
        barThickness: 2
      });
    };

    function getInfoForScalebar() {
      // Get info for scale bar
      const item = layers[0];
      // plugin assumes that the provided pixelsPerMeter is the one of the image at index 0 in world.getItemAt
      if (isRealValue(item.resolutionUnit)) {
        if (item.resolutionUnit === 3) {
          const pixPerCm = item.xResolution;
          setScaleBar(pixPerCm * 100);
          MICRONS_PER_PIX = 10000 / pixPerCm;
        } else {
          console.warn('resolutionUnit <> 3', item.resolutionUnit);
        }
      }
    }

    this.viewer = viewer; // SET THIS VIEWER
    this.overlay = this.viewer.fabricjsOverlay({ scale: 1000 });
    this.canvas = this.overlay.fabricCanvas();
    this.vInfo = viewerInfo;
  }

  /**
   * @return OpenSeadragon.Viewer
   */
  getViewer() {
    return this.viewer;
  }
}
