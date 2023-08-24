/**
 * Create popup interface and handle events.
 *
 * @param {object} divBody - The body of the div, which we will fill in here.
 * @param {Array} allLayers - Array of layers displayed in this viewer
 * @param {object} viewer - OpenSeadragon viewer
 */
const layerPopup = function(divBody, allLayers, viewer) {
  function switchRenderTypeIfNecessary() {
    // If the current render type is not by probability, switch it.
    if (STATE.renderType === 'byProbability') {
      STATE.renderType = 'byProbability';
    }
  }

  function createAttenuationBtn(allLayers, viewer) {
    // Color attenuation by probability
    const attId = createId(5, 'atten');
    const label = e('label', { for: attId });
    label.innerHTML = '&nbsp;&#58;&nbsp;color-attenuation by probability<br>';

    // Icon
    const icon = e('i', {
      id: attId,
      class: 'fas fa-broadcast-tower layer-icons',
      title: 'toggle: color-attenuation by probability'
    });

    // Event listener
    icon.addEventListener('click', () => {
      // Toggle attenuate state
      STATE.attenuate = !STATE.attenuate;
      // Ensure that either outline or attenuate is on, but not both.
      STATE.outline = false;
      switchRenderTypeIfNecessary();
      setFilter(allLayers, viewer);
    });
    return [label, icon];
  }

  // un/fill polygon
  function createOutlineBtn(allLayers, viewer) {
    const fillId = createId(5, 'fill');
    const label = e('label', { for: fillId });
    label.innerHTML = '&nbsp;&nbsp;&#58;&nbsp;un/fill polygon<br>';
    const emptyCircle = 'far';
    const filledCircle = 'fas';

    // Icon
    const icon = e('i', {
      id: fillId,
      class: `${filledCircle} fa-circle layer-icons`,
      title: 'fill un-fill'
    });

    // Event listener
    icon.addEventListener('click', () => {
      // Toggle outline state
      STATE.outline = !STATE.outline;
      // Ensure only one flag is active (either attenuate or outline; not both).
      STATE.attenuate = false;
      switchRenderTypeIfNecessary();
      toggleButton(icon, filledCircle, emptyCircle);
      setFilter(allLayers, viewer);
    });
    return [label, icon];
  }

  function createSlider(d, t, allLayers, viewer) {
    // Create range slider with two handles
    const wrapper = e('div', {
      class: d.class,
      role: 'group',
      'aria-labelledby': 'multi-lbl',
      style: `--${d.aLab}: ${d.aInit}; --${d.bLab}: ${d.bInit}; --min: ${d.min}; --max: ${d.max}`
    });

    const title = e('div', { id: 'multi-lbl' });
    title.innerHTML = t;
    wrapper.appendChild(title);

    const ARange = e('input', {
      id: d.aLab,
      type: 'range',
      min: d.min,
      max: d.max,
      value: d.aInit,
    });
    const BRange = e('input', {
      id: d.bLab,
      type: 'range',
      min: d.min,
      max: d.max,
      value: d.bInit,
    });

    const output1 = e('output', { for: d.aLab, style: `--c: var(--${d.aLab})` });
    const output2 = e('output', { for: d.bLab, style: `--c: var(--${d.bLab})` });

    wrapper.appendChild(ARange);
    wrapper.appendChild(output1);
    wrapper.appendChild(BRange);
    wrapper.appendChild(output2);

    function updateDisplay(e) {
      const input = e.target;
      const wrapper = input.parentNode;
      wrapper.style.setProperty(`--${input.id}`, +input.value);

      const slideVals = getVals([ARange, BRange]);

      if (d.type === 'outside') {
        setFilter(allLayers, viewer, { min: slideVals[0], max: slideVals[1], type: 'outside' });
      } else {
        setFilter(allLayers, viewer, { min: slideVals[0], max: slideVals[1], type: 'inside' });
      }
    }

    ARange.addEventListener('input', updateDisplay);
    BRange.addEventListener('input', updateDisplay);

    return wrapper;
  }

  // Append to body
  const [label1, atten] = createAttenuationBtn(allLayers, viewer);
  const [label2, fillPoly] = createOutlineBtn(allLayers, viewer);
  divBody.appendChild(e('div', {}, [atten, label1, fillPoly, label2]));

  // todo: scale initial values
  let d = {
    aLab: 'a',
    bLab: 'b',
    aInit: 70,
    bInit: 185,
    min: 0,
    max: MAX,
    class: 'dualSlider',
    type: 'inside',
  };
  const wrapper = createSlider(d, 'In range:', allLayers, viewer);

  d = {
    aLab: 'a1',
    bLab: 'b1',
    aInit: 10,
    bInit: 245,
    min: 0,
    max: MAX,
    class: 'dualSlider1',
    type: 'outside',
  };
  const section = createSlider(d, 'Out range:', allLayers, viewer);

  const dd = e('div', {}, [section, wrapper]);
  divBody.appendChild(dd);
};
