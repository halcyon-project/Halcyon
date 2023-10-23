/** Custom color filters */

const bgTrans = function(imageData) {
  for (let i = 0; i < imageData.length; i += 4) {
    if (imageData[i + 1] === 0) {
      imageData[i + 3] = 0;
    }
  }
  return imageData;
};

const img2arrayWithBackgroundCorrection = imgData => {
  return imgData.data.reduce((pixel, key, index) => {
    if (index % 4 === 0) {
      pixel.push([key]);
    } else {
      pixel[pixel.length - 1].push(key);
    }

    // Apply background correction if the RGBA values for the pixel are fully populated
    if (index % 4 === 3) {
      const px = pixel[pixel.length - 1];
      if (px[1] === 0 || px[3] === 0) {
        px[0] = 0;
        px[1] = 0;
        px[2] = 0;
        px[3] = 0;
      }
    }

    return pixel;
  }, []);
};

/**********************
 CUSTOM COLOR FILTERS
 **********************/
const colorChannel = 1;
const alphaChannel = 3;
const maxPixelValue = 255;

// Outline the edge of the polygon
OpenSeadragon.Filters.OUTLINE = rgba => {
  return (context, callback) => {
    // console.time('Outline');
    const width = context.canvas.width;
    const height = context.canvas.height;

    let imgData = context.getImageData(0, 0, width, height);
    let data = img2arrayWithBackgroundCorrection(imgData);
    let flatData = new Uint8ClampedArray(width * height * 4);

    for (let i = 0; i < data.length; i++) {
      let currentPixel = data[i];
      let isEdge = false;

      if (currentPixel[alphaChannel] === maxPixelValue && currentPixel[colorChannel] > 0) {
        // Index calculations
        let right = i + 1;
        let left = i - 1;
        let up = i - width;
        let down = i + width;

        if (data[right] && data[right][alphaChannel] === 0) isEdge = true;
        if (data[left] && data[left][alphaChannel] === 0) isEdge = true;
        if (data[up] && data[up][alphaChannel] === 0) isEdge = true;
        if (data[down] && data[down][alphaChannel] === 0) isEdge = true;

        if (isEdge) {
          // Set to the desired RGBA for outline
          currentPixel = rgba;
        }
      } else {
        // Set each pixel to transparent
        currentPixel = [0, 0, 0, 0];
      }

      // Flatten data as we go
      flatData.set(currentPixel, i * 4);
    }

    // Change the remaining colored pixels to transparent
    for (let i = 0; i < flatData.length; i += 4) {
      if (flatData[i + colorChannel] > 0) {
        flatData[i] = 0;
        flatData[i + 1] = 0;
        flatData[i + 2] = 0;
        flatData[i + 3] = 0;
      }
    }

    let newImage = context.createImageData(width, height);
    newImage.data.set(flatData);
    context.putImageData(newImage, 0, 0);

    // console.timeEnd('Outline');
    callback();
  };
};

// Handles 'inside' and 'outside' sliders
OpenSeadragon.Filters.PROBABILITY = (data, rgba) => {
  return (context, callback) => {
    // console.time('Probability');
    let imgData = context.getImageData(0, 0, context.canvas.width, context.canvas.height);
    let pixels = imgData.data;

    // Define the condition check based on the type
    let shouldColor;
    switch (data.type) {
      case 'inside':
        shouldColor = greenChannel => greenChannel > data.slideHandle1 && greenChannel <= data.slideHandle2;
        break;
      case 'outside':
        shouldColor = greenChannel => (greenChannel > 0 && greenChannel <= data.slideHandle1) || (greenChannel <= maxPixelValue && greenChannel >= data.slideHandle2);
        break;
      default:
        throw new Error(`Invalid type: ${data.type}`);
    }

    for (let i = 0; i < pixels.length; i += 4) {
      const probability = pixels[i + 1]; // green channel is "probability"

      if (shouldColor(probability)) {
        pixels[i] = rgba[0];
        pixels[i + 1] = rgba[1];
        pixels[i + 2] = rgba[2];
        pixels[i + 3] = rgba[3];
      } else {
        pixels[i + 3] = 0;
      }
    }

    context.putImageData(imgData, 0, 0);
    // console.timeEnd('Probability');
    callback();
  };
};

const getRangeColor = (channelValue, colorRanges, rgbas) => {
  for (let i = 0; i < colorRanges.length; i++) {
    if (channelValue >= colorRanges[i].low && channelValue <= colorRanges[i].high) {
      return rgbas[i];
    }
  }
  return [0, 0, 0, 0];
};

const getClassColor = (channelValue, greenChannel, classifications, rgbas) => {
  for (let i = 0; i < classifications.length; i++) {
    if (channelValue === classifications[i].classid && greenChannel >= classifications[i].val) {
      return rgbas[i];
    }
  }
  return [0, 0, 0, 0];
};

const setPixelData = (data, i, rgba, greenChannel, attenuate) => {
  data[i] = rgba[0];
  data[i + 1] = rgba[1];
  data[i + 2] = rgba[2];
  data[i + 3] = attenuate ? greenChannel : rgba[3];
};

const gradient = [[0, 0, 255, 255], [1, 0, 254, 255], [2, 0, 253, 255], [3, 0, 252, 255], [4, 0, 251, 255], [5, 0, 250, 255], [6, 0, 249, 255], [7, 0, 248, 255], [8, 0, 247, 255], [9, 0, 246, 255], [10, 0, 245, 255], [11, 0, 244, 255], [12, 0, 243, 255], [13, 0, 242, 255], [14, 0, 241, 255], [15, 0, 240, 255], [16, 0, 239, 255], [17, 0, 238, 255], [18, 0, 237, 255], [19, 0, 236, 255], [20, 0, 235, 255], [21, 0, 234, 255], [22, 0, 233, 255], [23, 0, 232, 255], [24, 0, 231, 255], [25, 0, 230, 255], [26, 0, 229, 255], [27, 0, 228, 255], [28, 0, 227, 255], [29, 0, 226, 255], [30, 0, 225, 255], [31, 0, 224, 255], [32, 0, 223, 255], [33, 0, 222, 255], [34, 0, 221, 255], [35, 0, 220, 255], [36, 0, 219, 255], [37, 0, 218, 255], [38, 0, 217, 255], [39, 0, 216, 255], [40, 0, 215, 255], [41, 0, 214, 255], [42, 0, 213, 255], [43, 0, 212, 255], [44, 0, 211, 255], [45, 0, 210, 255], [46, 0, 209, 255], [47, 0, 208, 255], [48, 0, 207, 255], [49, 0, 206, 255], [50, 0, 205, 255], [51, 0, 204, 255], [52, 0, 203, 255], [53, 0, 202, 255], [54, 0, 201, 255], [55, 0, 200, 255], [56, 0, 199, 255], [57, 0, 198, 255], [58, 0, 197, 255], [59, 0, 196, 255], [60, 0, 195, 255], [61, 0, 194, 255], [62, 0, 193, 255], [63, 0, 192, 255], [64, 0, 191, 255], [65, 0, 190, 255], [66, 0, 189, 255], [67, 0, 188, 255], [68, 0, 187, 255], [69, 0, 186, 255], [70, 0, 185, 255], [71, 0, 184, 255], [72, 0, 183, 255], [73, 0, 182, 255], [74, 0, 181, 255], [75, 0, 180, 255], [76, 0, 179, 255], [77, 0, 178, 255], [78, 0, 177, 255], [79, 0, 176, 255], [80, 0, 175, 255], [81, 0, 174, 255], [82, 0, 173, 255], [83, 0, 172, 255], [84, 0, 171, 255], [85, 0, 170, 255], [86, 0, 169, 255], [87, 0, 168, 255], [88, 0, 167, 255], [89, 0, 166, 255], [90, 0, 165, 255], [91, 0, 164, 255], [92, 0, 163, 255], [93, 0, 162, 255], [94, 0, 161, 255], [95, 0, 160, 255], [96, 0, 159, 255], [97, 0, 158, 255], [98, 0, 157, 255], [99, 0, 156, 255], [100, 0, 155, 255], [101, 0, 154, 255], [102, 0, 153, 255], [103, 0, 152, 255], [104, 0, 151, 255], [105, 0, 150, 255], [106, 0, 149, 255], [107, 0, 148, 255], [108, 0, 147, 255], [109, 0, 146, 255], [110, 0, 145, 255], [111, 0, 144, 255], [112, 0, 143, 255], [113, 0, 142, 255], [114, 0, 141, 255], [115, 0, 140, 255], [116, 0, 139, 255], [117, 0, 138, 255], [118, 0, 137, 255], [119, 0, 136, 255], [120, 0, 135, 255], [121, 0, 134, 255], [122, 0, 133, 255], [123, 0, 132, 255], [124, 0, 131, 255], [125, 0, 130, 255], [126, 0, 129, 255], [127, 0, 128, 255], [128, 0, 127, 255], [129, 0, 126, 255], [130, 0, 125, 255], [131, 0, 124, 255], [132, 0, 123, 255], [133, 0, 122, 255], [134, 0, 121, 255], [135, 0, 120, 255], [136, 0, 119, 255], [137, 0, 118, 255], [138, 0, 117, 255], [139, 0, 116, 255], [140, 0, 115, 255], [141, 0, 114, 255], [142, 0, 113, 255], [143, 0, 112, 255], [144, 0, 111, 255], [145, 0, 110, 255], [146, 0, 109, 255], [147, 0, 108, 255], [148, 0, 107, 255], [149, 0, 106, 255], [150, 0, 105, 255], [151, 0, 104, 255], [152, 0, 103, 255], [153, 0, 102, 255], [154, 0, 101, 255], [155, 0, 100, 255], [156, 0, 99, 255], [157, 0, 98, 255], [158, 0, 97, 255], [159, 0, 96, 255], [160, 0, 95, 255], [161, 0, 94, 255], [162, 0, 93, 255], [163, 0, 92, 255], [164, 0, 91, 255], [165, 0, 90, 255], [166, 0, 89, 255], [167, 0, 88, 255], [168, 0, 87, 255], [169, 0, 86, 255], [170, 0, 85, 255], [171, 0, 84, 255], [172, 0, 83, 255], [173, 0, 82, 255], [174, 0, 81, 255], [175, 0, 80, 255], [176, 0, 79, 255], [177, 0, 78, 255], [178, 0, 77, 255], [179, 0, 76, 255], [180, 0, 75, 255], [181, 0, 74, 255], [182, 0, 73, 255], [183, 0, 72, 255], [184, 0, 71, 255], [185, 0, 70, 255], [186, 0, 69, 255], [187, 0, 68, 255], [188, 0, 67, 255], [189, 0, 66, 255], [190, 0, 65, 255], [191, 0, 64, 255], [192, 0, 63, 255], [193, 0, 62, 255], [194, 0, 61, 255], [195, 0, 60, 255], [196, 0, 59, 255], [197, 0, 58, 255], [198, 0, 57, 255], [199, 0, 56, 255], [200, 0, 55, 255], [201, 0, 54, 255], [202, 0, 53, 255], [203, 0, 52, 255], [204, 0, 51, 255], [205, 0, 50, 255], [206, 0, 49, 255], [207, 0, 48, 255], [208, 0, 47, 255], [209, 0, 46, 255], [210, 0, 45, 255], [211, 0, 44, 255], [212, 0, 43, 255], [213, 0, 42, 255], [214, 0, 41, 255], [215, 0, 40, 255], [216, 0, 39, 255], [217, 0, 38, 255], [218, 0, 37, 255], [219, 0, 36, 255], [220, 0, 35, 255], [221, 0, 34, 255], [222, 0, 33, 255], [223, 0, 32, 255], [224, 0, 31, 255], [225, 0, 30, 255], [226, 0, 29, 255], [227, 0, 28, 255], [228, 0, 27, 255], [229, 0, 26, 255], [230, 0, 25, 255], [231, 0, 24, 255], [232, 0, 23, 255], [233, 0, 22, 255], [234, 0, 21, 255], [235, 0, 20, 255], [236, 0, 19, 255], [237, 0, 18, 255], [238, 0, 17, 255], [239, 0, 16, 255], [240, 0, 15, 255], [241, 0, 14, 255], [242, 0, 13, 255], [243, 0, 12, 255], [244, 0, 11, 255], [245, 0, 10, 255], [246, 0, 9, 255], [247, 0, 8, 255], [248, 0, 7, 255], [249, 0, 6, 255], [250, 0, 5, 255], [251, 0, 4, 255], [252, 0, 3, 255], [253, 0, 2, 255], [254, 0, 1, 255], [255, 0, 0, 255]];

OpenSeadragon.Filters.COLORLEVELS = (vInfo, layerColors) => {
  return (context, callback) => {
    // console.time('ColorLevels');
    let imgData = context.getImageData(0, 0, context.canvas.width, context.canvas.height);
    const data = bgTrans(imgData.data);
    const colorGroup = layerColors.filter(x => x.checked);
    const rgbas = colorGroup.map(element => colorToArray(element.color));

    const setPix = (vInfo, myFunction, colorMap) => {
      for (let i = 0; i < data.length; i += 4) {
        if (data[i + 3] === maxPixelValue) {
          const redChannel = data[i];
          const greenChannel = data[i + 1];
          const rgba = vInfo.STATE.renderType === 'byClass' ? myFunction(redChannel, greenChannel, colorGroup, rgbas) :
            vInfo.STATE.renderType === 'byProbability' ? myFunction(greenChannel, colorGroup, rgbas) :
              vInfo.STATE.renderType === 'byHeatmap' ? colorMap[greenChannel] :
                (console.error('renderType?', vInfo.STATE.renderType), [0, 0, 0, 0]);

          setPixelData(data, i, rgba, greenChannel, vInfo.STATE.attenuate);
        } else {
          data[i] = data[i + 1] = data[i + 2] = data[i + 3] = 0;
        }
      }
    };

    setPix(vInfo,
      vInfo.STATE.renderType === 'byClass' ? getClassColor :
        vInfo.STATE.renderType === 'byProbability' ? getRangeColor : {},
      vInfo.STATE.renderType === 'byHeatmap' ? gradient : {}
    );

    context.putImageData(imgData, 0, 0);
    // console.timeEnd('ColorLevels');
    callback();
  };
};

OpenSeadragon.Filters.THRESHOLDING = thresh => {
  return (context, callback) => {
    if (typeof thresh !== 'undefined') {
      // console.time('Thresholding');
      let imgData = context.getImageData(0, 0, context.canvas.width, context.canvas.height);
      let pixels = imgData.data;

      let color;
      if (isRealValue(thresh.rgba)) {
        color = thresh.rgba;
      } else {
        color = [126, 1, 0, 255]; // #7e0100 (Maroon)
      }

      let shouldColor;

      if (isRealValue(thresh.classId)) {
        // Test classId and probability value above threshold.
        shouldColor = (i) => pixels[i] === thresh.classId && pixels[i + 1] >= thresh.val;
      } else {
        // Test probability (green channel) value above threshold.
        shouldColor = (i) => pixels[i + 1] >= thresh.val;
      }

      for (let i = 0; i < pixels.length; i += 4) {
        if (shouldColor(i)) {
          pixels[i] = color[0];
          pixels[i + 1] = color[1];
          pixels[i + 2] = color[2];
          pixels[i + 3] = color[3];
        } else {
          pixels[i + 3] = 0;
        }
      }

      context.putImageData(imgData, 0, 0);
      // console.timeEnd('Thresholding');
      callback();
    } else {
      console.warn("thresh is undefined");
    }
  };
};
