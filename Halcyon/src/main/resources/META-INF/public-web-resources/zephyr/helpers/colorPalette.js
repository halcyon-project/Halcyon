import { getContext, cfg } from '../context.js';

export function colorPalette() {
  // Create the container for the custom dropdown
  let paletteContainer = document.createElement('div');
  paletteContainer.className = 'dd';  // Apply the dropdown class for styling
  paletteContainer.id = 'colorPalette';

  // Insert the container before the canvas element
  document.body.insertBefore(paletteContainer, document.querySelector('canvas'));

  const url = colorClassesUrl(cfg('useriri'));
  if (!url) {
    buildColorPalette(paletteContainer);
  } else {
    fetch(url, {
      method: 'GET',
      headers: {
        'Accept': 'application/ld+json',
        'Prefer': 'return=representation; shacl=https://halcyon.is/ns/AnnotationClassListShape'
      }
    })
      .then(response => {
        if (!response.ok) {
          throw new Error('Network response was not ok. Status code:' + response.status);
        }
        return response.json();
      })
      .then(data => {
        buildColorPalette(paletteContainer, data);
      })
      .catch(error => {
        console.error('There was a problem with the fetch operation:', error);
        console.error(url);
        buildColorPalette(paletteContainer);
      });
  }
}

/**
 * Derive the user's colorclasses resource from their IRI, swapping only the
 * path segment "user" for "users" — a plain string replace could hit the
 * host name or an unrelated segment.
 */
function colorClassesUrl(useriri) {
  if (!useriri) return null;
  try {
    const u = new URL(useriri);
    u.pathname = u.pathname
      .split('/')
      .map(segment => (segment === 'user' ? 'users' : segment))
      .join('/');
    return `${u.href.replace(/\/+$/, '')}/colorclasses`;
  } catch (err) {
    console.error('Cannot derive colorclasses URL from useriri:', useriri, err);
    return null;
  }
}

function buildColorPalette(paletteContainer, data) {
  // Clear existing content
  paletteContainer.innerHTML = '';

  let options;
  if (data && Array.isArray(data.hasAnnotationClass)) {
    options = [];
    // console.log("data:", JSON.stringify(data));

    // Add data-based options
    data.hasAnnotationClass.forEach(annotationClass => {
      if (annotationClass.hasClass) {
        const color = annotationClass.color;
        const name = annotationClass.hasClass.name;
        options.push({ value: `${color}:${name}`, text: name });
      }
    });
  } else {
    options = [
      { value: '#ffff00:Tumor', text: 'Tumor' },
      { value: '#ff0000:Lymphocyte', text: 'Lymphocyte' },
      { value: '#00ff00:Misc', text: 'Misc' },
      { value: '#0000ff:Background', text: 'Background' }
    ];
  }

  // Create dropdown button
  const dropdownButton = document.createElement('div');
  dropdownButton.className = 'dd-button';
  dropdownButton.textContent = '-- Select Color --';
  paletteContainer.appendChild(dropdownButton);

  // Create dropdown content container
  const dropdownContent = document.createElement('div');
  dropdownContent.className = 'dd-content';
  paletteContainer.appendChild(dropdownContent);

  // Add options to the dropdown content
  options.forEach(opt => {
    const optionDiv = document.createElement('div');
    optionDiv.dataset.color = opt.value.split(':')[0];
    optionDiv.textContent = opt.text;

    // Create color box
    const colorBox = document.createElement('div');
    colorBox.className = 'color-box';
    colorBox.style.backgroundColor = optionDiv.dataset.color;

    optionDiv.prepend(colorBox);
    dropdownContent.appendChild(optionDiv);
  });

  // Dropdown content shows up when clicked
  dropdownButton.addEventListener('click', () => {
    dropdownContent.style.display = dropdownContent.style.display === 'block' ? 'none' : 'block';
  });

  // Click a color / cancer type
  dropdownContent.addEventListener('click', (event) => {
    if (event.target && event.target.dataset.color) {
      const color = event.target.dataset.color;
      const text = event.target.textContent.trim();
      dropdownButton.textContent = text;
      dropdownContent.style.display = 'none';

      // Palette selection is per-viewer state on the active context (#32).
      const ctx = getContext();
      ctx.cancerColor = color;
      ctx.cancerType = text;
    }
  });

  // Close dropdown if clicked outside
  window.addEventListener('click', (event) => {
    if (!event.target.matches('.dd-button')) {
      if (dropdownContent.style.display === 'block') {
        dropdownContent.style.display = 'none';
      }
    }
  });
}

export function getColorAndType() {
  let color, type;
  // Set the color and type before starting to draw. The context carries the
  // palette selection; the flat globals remain as a legacy-page fallback.
  const ctx = getContext();
  const selected = (ctx && ctx.cancerColor) || window.cancerColor;
  if (selected && selected.length > 0) {
    color = selected;
    type = (ctx && ctx.cancerColor) ? ctx.cancerType : window.cancerType;
  } else {
    color = "#0000ff";
    type = "";
  }
  return {color, type};
}
