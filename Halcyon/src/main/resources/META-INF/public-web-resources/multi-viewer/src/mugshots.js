/**
 * Create an array of ROIs.
 * Upon click of an ROI, the WSI will pan/zoom to the location and draw a box around the ROI.
 *
 * @param {object} options
 * @param {string} options.divId - example: "contentDiv"
 * @param {string} options.thumbId - example: "thumbnail-container"
 * @param {string} options.infoUrl - example: "https://iiif.princeton.edu/loris/iiif/2/pudl0001/4609321/s42/00000001.jp2"
 * @param {object} options.imgDims - example: { "@context": "http://iiif.io/api/image/2/context.json", "@id": "https://iiif.princeton.edu/loris/iiif/2/pudl0001/4609321/s42/00000001.jp2", protocol: "http://iiif.io/api/image", width: 5233, height: 7200, sizes: (7) […], tiles: (1) […], profile: (2) […] }
 * @param {number} options.thumbnailSize - example: 256
 * @param {number} options.scrollerLength - example: 10
 * @param {Array} options.mugshotArray - example: [] (TODO - still need mugshotArray?)
 * @param {string} options.roiColor - example: "#0f0"
 * @param {object} options.viewer - OpenSeadragon viewer
 */
const mugshots = function(options) {

  const canvas = (this.__canvas = options.overlay.fabricCanvas());
  options.overlay.resizeCanvas = function() {
    // Function override: Resize overlay canvas
    const image1 = this._viewer.world.getItemAt(0);

    this._fabricCanvas.setWidth(this._containerWidth);
    this._fabricCanvas.setHeight(this._containerHeight);
    this._fabricCanvas.setZoom(image1.viewportToImageZoom(this._viewer.viewport.getZoom(true)));

    const image1WindowPoint = image1.imageToWindowCoordinates(new OpenSeadragon.Point(0, 0));
    const canvasOffset = this._canvasdiv.getBoundingClientRect();
    const pageScroll = OpenSeadragon.getPageScroll();

    this._fabricCanvas.absolutePan(
      new fabric.Point(
        canvasOffset.left - Math.round(image1WindowPoint.x) + pageScroll.x,
        canvasOffset.top - Math.round(image1WindowPoint.y) + pageScroll.y,
      ),
    );
  };

  const vpt = options.viewer.viewport;

  const size = '256,';
  const rotation = '0';
  const quality = 'default';
  const format = 'jpg';

  createScroller(options.imgDims);

  function createScroller(data) {
    let ul;
    let li;
    let span;

    const fragment = document.createDocumentFragment();
    ul = document.createElement('ul');
    ul.classList.add('thumbnail-list');
    fragment.appendChild(ul);

    for (let j = 0; j < options.scrollerLength; j++) {
      li = document.createElement('li');
      ul.appendChild(li);
      span = document.createElement('span');
      // Giving it some number in the middle of the image
      // createThumbnail(data, span, Math.round(data.width / 2), Math.round(data.height / 2)) // Image coordinates
      createThumbnail(data, span);
      li.appendChild(span);
    }
    document.getElementById(options.thumbId).appendChild(fragment);
  }

  function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1) + min);
  }

  function xyExist(x, y) {
    return typeof x !== 'undefined' && typeof y !== 'undefined' && x >= 0 && y >= 0;
  }

  function createThumbnail(data, span, x, y) {
    let imageRect; // it's a rectangle
    if (xyExist(x, y)) {
      // x,y,w,h
      imageRect = new OpenSeadragon.Rect(x, y, options.thumbnailSize, options.thumbnailSize);
    } else {
      imageRect = randomImageRectangle(data);
    }
    checkWholeNumbers(imageRect);

    const imgElement = document.createElement('IMG');
    imgElement.alt = 'mugshot';
    imgElement.classList.add('thumbnail-image');

    imgElement.src = getImageUrl(options.infoUrl, imageRect);

    span.appendChild(imgElement);

    // highlightLocation(imageRect)

    imgElement.addEventListener('click', () => {
      showThumbnailOnImage(imageRect);
    });
  }

  function checkWholeNumbers(imageRect) {
    // IIIF wants whole numbers
    const imagePoint = imageRect.getTopLeft();
    if (imagePoint.x % 1 !== 0) {
      console.warn(imagePoint.x, 'not a whole number');
    }
    if (imagePoint.y % 1 !== 0) {
      console.warn(imagePoint.y, 'not a whole number');
    }
  }

  function getImageUrl(infoUrl, imageRect) {
    return `${infoUrl}/${imageRect.getTopLeft().x},${imageRect.getTopLeft().y},${imageRect.width},${
      imageRect.height
    }/${size}/${rotation}/${quality}.${format}`;
  }

  function showThumbnailOnImage(imageRect) {
    zoomToLocation(imageRect);
    highlightLocation(imageRect);
  }

  function zoomToLocation(imageRect) {
    const vptRect = vpt.imageToViewportRectangle(imageRect);
    vpt.panTo(vptRect.getCenter());
    vpt.zoomTo(vpt.getMaxZoom());
  }

  function highlightLocation(imageRect) {
    // Translate coordinates => image to viewport coordinates.
    // const vptRect = vpt.imageToViewportRectangle(imageRect)
    // const webRect = vpt.viewportToViewerElementRectangle(vptRect)
    // canvas.add(
    //   new fabric.Rect({
    //     stroke: options.roiColor,
    //     strokeWidth: 1,
    //     fill: '',
    //     left: webRect.x,
    //     top: webRect.y,
    //     width: webRect.width,
    //     height: webRect.height
    //   })
    // )

    // NOTE: With resizeCanvas override, use imageRect
    canvas.add(
      new fabric.Rect({
        stroke: options.roiColor,
        strokeWidth: 2,
        fill: '',
        left: imageRect.x,
        top: imageRect.y,
        width: imageRect.width,
        height: imageRect.height
      })
    );

    canvas.renderAll();
  }

  function randomImageRectangle(data) {
    // Give it plenty of room from the edge
    const padding = options.thumbnailSize * 2; // 512
    const left = getRandomInt(padding, data.width - padding);
    const top = getRandomInt(padding, data.height - padding);

    return new OpenSeadragon.Rect(left, top, options.thumbnailSize, options.thumbnailSize);
  }
};
