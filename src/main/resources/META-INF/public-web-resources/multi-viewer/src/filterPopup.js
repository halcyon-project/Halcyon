/**
 * Clicking the color palette icon brings you here.
 * Create a popup div allowing user to adjust color ranges for that layer,
 * or adjust the colors being used to color each class in that layer.
 *
 * @param {object} paletteBtn - The DOM element
 * @param {string} title - For the title bar of the floating div
 * @param {object} colorscheme - Object containing array of "colors" and array of "colorspectrum", to use for the classifications and color ranges, respectively.
 * @param {Array} viewerLayers - Array of layers (images) to be displayed in this viewer
 * @param {object} viewer - OpenSeadragon Viewer
 * @returns {object} popup - div
 */
const filterPopup = (paletteBtn, title, colorscheme, viewerLayers, viewer) => {
  /*
  Popup Div For Color Levels Naming Convention:
  markerXXX0 <- 0th row elements
  lowXXX0 <- 0th row elements
  hiXXX0 <- 0th row elements
  iXXX0 <- 0th row elements
  */
  setChecked(colorscheme);
  const uniqueId = getRandomInt(100, 999);
  const popup = createPopup(uniqueId, paletteBtn, title);
  const popupBody = popup.lastChild; // known
  const classDiv = e('div');
  const probabilityDiv = e('div');
  const heatmapDiv = e('div');
  const thresholdDiv = e('div');

  // <select>
  const selectList = createDropdown(
    uniqueId,
    [classDiv, probabilityDiv, heatmapDiv, thresholdDiv],
    viewerLayers,
    viewer,
  );
  popupBody.appendChild(selectList);

  // By class
  classDiv.style.display = STATE.renderType === 'byClass' ? 'block' : 'none';
  popupBody.appendChild(classDiv);
  createUI(uniqueId, classDiv, colorscheme.colors, viewerLayers, viewer, 'byClass');

  // By probability
  probabilityDiv.style.display = STATE.renderType === 'byProbability' ? 'block' : 'none';
  popupBody.appendChild(probabilityDiv);
  createUI(
    uniqueId,
    probabilityDiv,
    colorscheme.colorspectrum,
    viewerLayers,
    viewer,
    'byProbability',
  );

  // By heatmap, blue to red
  const msg = "Color gradient where reddish colors<br>" +
    "correspond to sureness and<br>" +
    "bluish colors to unsureness.";
  heatmapDiv.style.display = STATE.renderType === 'byHeatmap' ? 'block' : 'none';
  popupBody.appendChild(heatmapDiv);
  heatmapDiv.innerHTML = `<p style="color: #ffffff; background: -webkit-linear-gradient(#FF0000, #0000FF);">${msg}</p>`;

  // By threshold
  thresholdDiv.style.display = STATE.renderType === 'byThreshold' ? 'block' : 'none';
  popupBody.appendChild(thresholdDiv);

  createThresh(thresholdDiv, viewerLayers, viewer); // no cp

  return popup;
};

function createPopup(uniqueId, paletteBtn, title) {
  const widgetId = `filters${uniqueId}`;
  const rect = paletteBtn.getBoundingClientRect();
  // const title = `${title} color levels`;
  // return createDraggableDiv(widgetId, title, rect.left, rect.top);
  return createDraggableDiv(widgetId, title, rect.left, rect.top);
}

function setChecked(colorscheme) {
  // colorscheme.colors: is an array of class-colors objects
  // Now, add a flag called 'checked', and set it to true for use later:
  colorscheme.colors.map(a => {
    // eslint-disable-next-line no-return-assign
    return (a.checked = true);
  });

  // colorscheme.colorspectrum: is an array of color objects for probability
  // Add 'checked'. Add 'classid' - set value to current index in array.
  colorscheme.colorspectrum.forEach((element, index) => {
    element.checked = true;
    element.classid = index; // 'classid' also exists in colorscheme.colors.
    // We're overloading the variable, so we can have 1 checkbox handler for both.
  });
}

function createThresh(div, layers, viewer, colorPicker, classId) {
  const val = '1'; // 128
  let color;
  if (colorPicker) {
    color = colorToArray(colorPicker.style.backgroundColor);
    if (color.length === 3) {
      color.push(255);
    }
  } else {
    color = [126, 1, 0, 255];
  }

  // slider value
  const number = e('input', {
    type: 'number',
    min: '0',
    max: MAX.toString(),
    step: '1',
    size: '5',
    value: val,
  });

  // slider
  const range = e('input', {
    type: 'range',
    min: '0',
    max: MAX.toString(),
    step: '1',
    value: val,
  });

  div.appendChild(e('div', {}, [number, range]));
  number.addEventListener('input', function() {
    range.value = this.value;
    setFilter(layers, viewer, {}, { val: parseInt(this.value), rgba: color, classId: classId });
    // console.log('number input');
  });

  range.addEventListener('input', function() {
    number.value = this.value;
    setFilter(layers, viewer, {}, { val: parseInt(this.value), rgba: color, classId: classId });
    // console.log('range input');
  });
}

function checkboxHandler(checkboxElement, displayColors, layers, viewer) {
  checkboxElement.addEventListener('click', () => {
    // look up color by 'classid', set 'checked' to the state of the checkbox
    displayColors.find(x => x.classid === parseInt(checkboxElement.value)).checked =
      checkboxElement.checked;
    setFilter(layers, viewer);
  });
}

function createDropdown(uniqueId, divArr, allLayers, viewer) {
  const selectDiv = e('div', { style: 'display: block;' });
  const id = `select${uniqueId}`;
  selectDiv.innerHTML = `<label for="${id}">Color by:</label>&nbsp;`;

  // Array of options to be added
  const selectList = e('select');
  selectList.id = id;
  selectDiv.appendChild(selectList);

  // Append the options
  RENDER_TYPES.forEach((option, i) => {
    const element = document.createElement('option');
    element.setAttribute('value', option);
    element.text = option.startsWith('by') ? option.replace('by', '') : option;
    selectList.appendChild(element);
  });

  // An option was selected
  selectList.addEventListener('change', () => {
    // set global type
    STATE.renderType = selectList.options[selectList.selectedIndex].value;
    // no outline for you
    STATE.outline = false;

    // Shut all off...
    divArr.forEach(div => {
      div.style.display = 'none';
    });

    // ...and turn one on.
    if (STATE.renderType === 'byClass') {
      divArr[0].style.display = 'block';
    } else if (STATE.renderType === 'byProbability') {
      divArr[1].style.display = 'block';
    } else if (STATE.renderType === 'byHeatmap') {
      divArr[2].style.display = 'block';
    } else if (STATE.renderType === 'byThreshold') {
      divArr[3].style.display = 'block';
    }

    // Initial values set
    if (STATE.renderType === 'byThreshold') {
      setFilter(allLayers, viewer, {}, { val: 1, rgba: [126, 1, 0, 255] }); // 128
    } else {
      setFilter(allLayers, viewer);
    }
  });

  return selectDiv;
}

// Create user interface
function createUI(uniq, div, layerColors, layers, viewer, type) {
  const table = e('table', { class: 'popupBody' });
  div.appendChild(table);
  const byProb = type === 'byProbability';
  const byClass = type === 'byClass';

  if (layerColors) {
    // Different headers
    if (byClass) {
      table.appendChild(createHeaderRow(['', 'Color', 'Label', 'Pixels with certainty >= n']));
    } else if (byProb) {
      layerColors.sort((a, b) => b.low - a.low);
      table.appendChild(createHeaderRow(['', 'Color', 'Low', 'High']));
    }

    // Create table row for each color rgba; allow user to adjust color
    layerColors.forEach((colorObject, cIdx) => {
      const checkbox = e('input', {
        type: 'checkbox',
        name: `classes${uniq}`,
        value: colorObject.classid
      });

      if (colorObject.checked) {
        checkbox.setAttribute('checked', true);
      } else {
        checkbox.removeAttribute('checked');
      }

      const colorPicker = createColorPicker(cIdx, uniq, colorObject, layers, viewer);

      let tr;
      let numLow;
      let numHigh;
      let removeBtn;

      if (byProb) {
        // adjust range (low to high)
        numLow = createNumericInput(
          `low${uniq}${cIdx}`,
          table,
          uniq,
          layers,
          colorObject,
          layerColors,
          viewer
        );

        numHigh = createNumericInput(
          `high${uniq}${cIdx}`,
          table,
          uniq,
          layers,
          colorObject,
          layerColors,
          viewer
        );

        const buttonId = `i${uniq}${cIdx}`;
        // button to add or remove a range
        removeBtn = e('i', { id: buttonId, class: 'fas fa-minus pointer' });

        tr = e('tr', {}, [
          e('td', {}, [checkbox]),
          e('td', {}, [colorPicker]),
          e('td', {}, [numLow]),
          e('td', {}, [numHigh]),
          e('td', {}, [removeBtn]),
        ]);
      } else if (byClass) {
        let d = e('div');
        createThresh(d, layers, viewer, colorPicker, colorObject.classid);
        tr = e('tr', {}, [
          e('td', {}, [checkbox]),
          e('td', {}, [colorPicker]),
          e('td', {}, [e('span', {}, [colorObject.name])]),
          e('td', {}, [d])
        ]);
      }
      table.appendChild(tr);

      checkboxHandler(checkbox, layerColors, layers, viewer);

      if (byProb) {
        // TR has been defined, now we can use it
        removeBtn.addEventListener(
          'click',
          removeColor.bind(null, removeBtn, layerColors, tr, layers, viewer),
          {
            passive: true,
          }
        );
      }
    });

    // After all that is done...
    if (byProb) {
      table.appendChild(extraRow(uniq, layerColors, layers, viewer));
    }
  } else {
    console.warn('Layer colors?', layerColors);
  }
  // Done.
}

function removeColor(el, ourRanges, tr, layers, viewer) {
  const str = el.id;
  const n = str.charAt(str.length - 1);
  ourRanges.splice(parseInt(n), 1); // remove from list
  tr.remove(); // remove table row
  setFilter(layers, viewer); // reflect changes in viewer
}

// Create sortable header row
function createHeaderRow(tableHeaders) {
  const row = e('tr');

  for (let i = 0; i < tableHeaders.length; i++) {
    const th = e('th', { class: 'pointer' });
    th.innerHTML = tableHeaders[i];
    row.appendChild(th);

    th.addEventListener('click', () => {
      const table = th.closest('table');
      Array.from(table.querySelectorAll('tr:nth-child(n+2)'))
        .sort(comparer(Array.from(th.parentNode.children).indexOf(th), (this.asc = !this.asc)))
        .forEach(tr => table.appendChild(tr));
    });
  }

  return row;
}

const getCellValue = (tr, idx) => {
  const td = tr.children[idx];
  if (td.children[0]) {
    if (td.children[0].type === 'number') {
      // sort by number
      return td.children[0].value;
    }
    // sort by alpha
    return td.innerText || td.textContent;
  }
  // disable for this column
  return '';
};

const comparer = (idx, asc) => (a, b) =>
  ((v1, v2) =>
    v1 !== '' && v2 !== '' && !isNaN(v1) && !isNaN(v2) ? v1 - v2 : v1.toString().localeCompare(v2))(
  getCellValue(asc ? a : b, idx),
  getCellValue(asc ? b : a, idx)
);

// Create color picker input
function createColorPicker(cIdx, uniq, colorObject, layers, viewer) {
  let init = true;
  const m = e('mark', { id: `marker${uniq}${cIdx}` });
  const colorCode = colorObject.color;
  m.style.backgroundColor = colorCode;
  m.innerHTML = `#${rgba2hex(colorCode)}`;

  function handleColorChange(r, g, b, a) {
    if (init) {
      init = false; // Update the state
      return;
    }
    // console.log([r, g, b, a])
    this.source.value = this.color(r, g, b, a);
    this.source.innerHTML = this.color(r, g, b, a);
    this.source.style.backgroundColor = this.color(r, g, b, a);
    colorObject.color = `rgba(${r}, ${g}, ${b}, ${a * 255})`;
    // console.log('colorObject', colorObject)
    setFilter(layers, viewer);
  }

  const picker = new CP(m);
  picker.on('change', handleColorChange);

  return m;
}

// Our colors are in rgba - convert to hex for color picker element
function rgba2hex(orig) {
  let a;
  const arr = orig.replace(/\s/g, '').match(/^rgba?\((\d+),(\d+),(\d+),?([^,\s)]+)?/i);
  const alpha = ((arr && arr[4]) || '').trim();
  let hex = arr
    ? (arr[1] | (1 << 8)).toString(16).slice(1)
      + (arr[2] | (1 << 8)).toString(16).slice(1)
      + (arr[3] | (1 << 8)).toString(16).slice(1)
    : orig;

  if (alpha !== '') {
    a = alpha;
  } else {
    a = 1;
  }
  a = (a | (1 << 8)).toString(16).slice(1);
  hex += a;
  return hex;
}

// Last stop before "set filter"
function numericEvent(numEl, colorObject, layers, viewer) {
  const intVal = parseInt(numEl.value);

  // If they set it to something outside 0-MAX, reset it
  if (intVal > MAX) numEl.value = MAX.toString();
  if (intVal < 0) numEl.value = '0';

  if (numEl.id.startsWith('low')) colorObject.low = intVal;
  if (numEl.id.startsWith('high')) colorObject.high = intVal;

  setFilter(layers, viewer);
}

// Create numeric input
function createNumericInput(id, table, uniq, layers, colorObject, colors, viewer) {
  let val;
  if (!colorObject.low && !colorObject.high) {
    val = '';
  } else {
    val = id.includes('low') ? colorObject.low.toString() : colorObject.high.toString();
  }

  const numEl = e('input', {
    id,
    type: 'number',
    min: '0',
    max: MAX.toString(),
    step: '1',
    size: '5',
    value: val,
  });

  // Event listeners
  // numEl.addEventListener('change', isIntersect.bind(null, table), {passive: true})
  numEl.addEventListener('input', numericEvent.bind(null, numEl, colorObject, layers, viewer), {
    passive: true
  });
  return numEl;
}

// An interval has low value and high value
// Check to see if any two intervals overlap
function isIntersect(table) {
  const arr = [];
  const cells = table.getElementsByTagName('td');
  for (const cell of cells) {
    const elem = cell.children[0];
    if (elem.type === 'number') {
      elem.style.outlineStyle = '';
      elem.style.outlineColor = '';
      if (elem.id.startsWith('low')) {
        arr.push({ low: elem, lowVal: parseInt(elem.value) });
      }
      if (elem.id.startsWith('high')) {
        const el = arr[arr.length - 1]; // get last array elem
        el.high = elem;
        el.highVal = parseInt(elem.value);
      }
    }
  }

  // Sort before validate
  arr.sort((a, b) => {
    return b.lowVal - a.lowVal;
  });
  const n = arr.length - 1;
  for (let i = 0; i < n; i++) {
    const notZeroes = arr[i].lowVal !== 0 && arr[i + 1].highVal !== 0;
    // If low <= high of next, then overlap
    if (arr[i].lowVal <= arr[i + 1].highVal && notZeroes) {
      setOutlineStyle(arr[i].low, arr[i + 1].high, 'solid', 'red');
      return true;
    }
  }

  return false;
}

// The outline around the inputs for numbers - red for error, clear for default
function setOutlineStyle(a, b, style, color) {
  // For numeric element pair
  if (isRealValue(a)) {
    a.style.outlineStyle = style;
    a.style.outlineColor = color;
  }
  if (isRealValue(b)) {
    b.style.outlineStyle = style;
    b.style.outlineColor = color;
  }
}

// The "Add color range" event
function addColor(idx, num1, num2, cpEl, chkEl, uniq, tr, colors, layers, viewer) {
  setOutlineStyle(num1, num2, '', ''); // clear any error
  if (num1.value === '0' && num2.value === '0') {
    // indicate 0 and 0 not allowed
    setOutlineStyle(num1, num2, 'solid', 'red');
  } else {
    // Now replace + with - in UI
    const buttonId = `i${num1.id.replace('low', '')}`; // borrowing element id
    const removeBtn = e('i', { id: buttonId, class: 'fas fa-minus pointer' });
    tr.lastChild.firstChild.remove(); // last element in row is modifier
    tr.lastChild.appendChild(removeBtn); // replace old modifier with new one
    removeBtn.addEventListener(
      'click',
      removeColor.bind(null, removeBtn, colors, tr, layers, viewer),
      {
        passive: true,
      }
    );

    checkboxHandler(chkEl, colors, layers, viewer);

    // add another empty row
    const table = tr.closest('table');
    table.appendChild(extraRow(uniq, colors, layers, viewer));
  }
}

// sequence
function seq(objArray) {
  const arr = objArray.map(a => a.classid);
  arr.sort();
  const [min, max] = [Math.min(...arr), Math.max(...arr)];
  return Array.from(Array(max - min), (v, i) => i + min).filter(i => !arr.includes(i));
}

// Extra row for adding color and range values
function extraRow(uniq, colors, layers, viewer) {
  let idx;
  const nums = seq(colors); // Use 'const'.
  if (!nums || isEmpty(nums)) {
    idx = colors.length;
  } else {
    idx = Array.isArray(nums) ? nums[0] : nums;
  }

  const colorObject = {
    color: 'rgba(255, 255, 255, 255)',
    low: 0,
    high: 0,
    checked: false,
    classid: idx, // overloading 'classid'
  };
  colors.push(colorObject);

  const chkEl = e('input', {
    type: 'checkbox',
    name: `classes${uniq}`,
    value: idx,
  });
  checkboxHandler(chkEl, colors, layers, viewer);

  const cpEl = createColorPicker(idx, uniq, colorObject, layers, viewer);
  const b = document.getElementById(`filters${uniq}Body`);
  const t = b.firstChild;
  const num1 = createNumericInput(`low${uniq}${idx}`, t, uniq, layers, colorObject, colors, viewer);
  const num2 = createNumericInput(
    `high${uniq}${idx}`,
    t,
    uniq,
    layers,
    colorObject,
    colors,
    viewer
  );
  const addBtn = e('i', { id: `i${uniq}${idx}`, class: 'fas fa-plus pointer' });

  const tr = e('tr', {}, [
    e('td', {}, [chkEl]),
    e('td', {}, [cpEl]),
    e('td', {}, [num1]),
    e('td', {}, [num2]),
    e('td', {}, [addBtn]),
  ]);

  addBtn.addEventListener(
    'click',
    addColor.bind(null, idx, num1, num2, cpEl, chkEl, uniq, tr, colors, layers, viewer),
    { passive: true }
  );

  return tr;
}
