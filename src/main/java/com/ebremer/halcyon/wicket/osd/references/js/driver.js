/*function componentToHex(c) {
  var hex = c.toString(16);
  return hex.length == 1 ? "0" + hex : hex;
}

function rgbToHex(r, g, b) {
  return "#" + componentToHex(r) + componentToHex(g) + componentToHex(b);
}

function hexToRgb(hex) {
  var result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : null;
}

console.log(hexToRgb("#FF3522").g);
*/
var viewer1 = {};
var x = OpenSeadragon.Filters.GREYSCALE;
x.prototype.COLORIZE2 = function (r, g, b) {
        return function (context, callback) {
                var imgData = context.getImageData(0, 0, context.canvas.width, context.canvas.height);
                var pixels = imgData.data;
                for (var i = 0; i < pixels.length; i += 4) {
                        var avg = pixels[i] / 255;
                        if (pixels[i + 3] == 255) {
                                pixels[i]     = r * avg;
                                pixels[i + 1] = g * avg;
                                pixels[i + 2] = b * avg;
                                pixels[i + 3] = 255 * avg;
                        } else if (pixels[i] > 0) {
                                pixels[i + 3] = 0;
                        }
                }
                context.putImageData(imgData, 0, 0);
                callback();
        };
};

function selectCancerType() {
        //var x = document.getElementById("cancertype").value;
        //var select = document.getElementById("imageids");
        //select.innerHTML = "";
        //var nl = tcgalist[x];
        //for (i = 0; i < nl.length; i++) {
        //        var option = document.createElement("option");
        //        option.text = nl[i].id;
         //       select.add(option);
        //}
        //console.log("You selected: " + x + " which has " + select.options.length + " images");
        //var image = document.getElementById("imageids");
        //var imageid = document.getElementById("imageids").value;
        //image.onchange();
}

function selectImage() {
        //var ctype = document.getElementById("cancertype").value;
        //var x = document.getElementById("imageids").value;
        //console.log("setting viewer to image : " + x);
        //var ti = "https://quip.bmi.stonybrook.edu/iiif/?iiif=/tcgaseg/tcgaimages/" + ctype + "/" + x + ".svs/info.json";
        //var si = "https://quip.bmi.stonybrook.edu/iiif/?iiif=/tcgaseg/featureimages/" + ctype + "/" + x + "-featureimage.tif/info.json";
        //viewerA.open([ti, si]);
}

function setViewer(id, imageArray, opacityArray) {
        let viewer = {};
        viewer = OpenSeadragon({ id: id, prefixUrl: "//openseadragon.github.io/openseadragon/images/" });
        imageArray.forEach(function (image, index) {
                viewer.addTiledImage({ tileSource: image, opacity: opacityArray ? opacityArray[index] : 0, x: 0, y: 0 });
        });
        viewer.world.addHandler("add-item", function (data) {
                if (viewer1.world._items.length == 2) {
                        viewer.setFilterOptions({
                                filters: [{
                                        items: viewer.world.getItemAt(1),
                                        processors: [
                                                x.prototype.COLORIZE2(0,255,0)
                                        ]
                                }]
                        });
                        viewer1.world.getItemAt(1).source.getTileUrl = function (level, x, y) {
                                var IIIF_ROTATION = '0',
                                        scale = Math.pow(0.5, this.maxLevel - level),
                                        levelWidth = Math.ceil(this.width * scale),
                                        levelHeight = Math.ceil(this.height * scale),
                                        tileSize,
                                        iiifTileSizeWidth,
                                        iiifTileSizeHeight,
                                        iiifRegion,
                                        iiifTileX,
                                        iiifTileY,
                                        iiifTileW,
                                        iiifTileH,
                                        iiifSize,
                                        iiifQuality,
                                        uri;
                                tileSize = this.getTileWidth(level);
                                iiifTileSizeWidth = Math.ceil(tileSize / scale);
                                iiifTileSizeHeight = iiifTileSizeWidth;
                                iiifQuality = "default.png";
                                if (levelWidth < tileSize && levelHeight < tileSize) {
                                        iiifSize = levelWidth + ",";
                                        iiifRegion = 'full';
                                } else {
                                        iiifTileX = x * iiifTileSizeWidth;
                                        iiifTileY = y * iiifTileSizeHeight;
                                        iiifTileW = Math.min(iiifTileSizeWidth, this.width - iiifTileX);
                                        iiifTileH = Math.min(iiifTileSizeHeight, this.height - iiifTileY);
                                        iiifSize = Math.ceil(iiifTileW * scale) + ",";
                                        iiifRegion = [iiifTileX, iiifTileY, iiifTileW, iiifTileH].join(',');
                                }
                                uri = [this['@id'], iiifRegion, iiifSize, IIIF_ROTATION, iiifQuality].join('/');
                                return uri;
                        };
                }
        });
        return viewer;
}

function demo() {
        viewer1 = setViewer("viewer1", [
		"http://localhost:8080/iiif/?iiif=http://localhost:8080/web/TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.svs/info.json",
        "http://localhost:8080/halcyon/?iiif=http://localhost:8080/web/TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.svs/info.json"
], [1.0, 1.0]);
        return [viewer1];
}

