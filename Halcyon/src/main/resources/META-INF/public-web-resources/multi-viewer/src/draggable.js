/**
 * Create floating div user interface.
 * Return the created div back to the calling program.
 * Calling program will create an HTML table and attach it to the body.
 *
 * @example Popup Div Naming Convention
 * nameXXX
 * nameXXXHeader
 * nameXXXBody
 *
 * @param {string} prfx - ID prefix to be used in the created elements
 * @param {string} title - Header title
 * @param {number} left - The left edge of the positioned <div> element
 * @param {number} top - The top edge of the positioned <div> element
 * @param {boolean} viz - Visibility
 * @returns {object} myDiv - The floating div
 */
function createDraggableDiv(prfx, title, left, top, viz = false) {
  const myDiv = e('div', { class: 'popup', id: prfx });
  myDiv.style.left = `${left}px`;
  myDiv.style.top = `${top}px`;

  const myImg = e('img', {
    src: `${CONFIG.appImages}close-icon.png`,
    alt: 'close',
    height: 25,
    width: 25,
  });
  myImg.style.cursor = 'pointer';
  myImg.addEventListener('click', () => {
    myDiv.style.display = 'none';
  });

  const myHeader = e('div', { class: 'popupHeader', id: `${prfx}Header` }, [
    myImg,
    e('span', {}, [title]),
  ]);
  myDiv.appendChild(myHeader);

  const body = e('div', { id: `${prfx}Body`, style: 'padding: 10px;' });
  // "body" to be filled in by calling function
  myDiv.appendChild(body);
  document.body.appendChild(myDiv);
  if (!viz) {
    myDiv.style.display = 'none'; // This gets toggled
  }

  // Make the DIV element draggable
  dragElement(myDiv);

  return myDiv;
}

function dragElement(_elem) {
  let pos1 = 0;
  let pos2 = 0;
  let pos3 = 0;
  let pos4 = 0;
  // Note the naming convention
  if (document.getElementById(`${_elem.id}Header`)) {
    // if present, the header is where you move the DIV from:
    document.getElementById(`${_elem.id}Header`).onmousedown = dragMouseDown;
  } else {
    // otherwise, move the DIV from anywhere inside the DIV:
    _elem.onmousedown = dragMouseDown;
  }

  // Mouse-down handler
  function dragMouseDown(evt) {
    evt.preventDefault();
    // get the mouse cursor position at startup:
    pos3 = evt.clientX;
    pos4 = evt.clientY;
    document.onmouseup = closeDragElement;
    // call a function whenever the cursor moves:
    document.onmousemove = elementDrag;
  }

  // Mouse-move handler
  function elementDrag(evt) {
    evt.preventDefault();
    // calculate the new cursor position:
    pos1 = pos3 - evt.clientX;
    pos2 = pos4 - evt.clientY;
    pos3 = evt.clientX;
    pos4 = evt.clientY;
    // set the element's new position:
    _elem.style.top = `${_elem.offsetTop - pos2}px`;
    _elem.style.left = `${_elem.offsetLeft - pos1}px`;
  }

  // Mouse-up handler
  function closeDragElement() {
    // stop moving when mouse button is released:
    document.onmouseup = null;
    document.onmousemove = null;
  }
}
