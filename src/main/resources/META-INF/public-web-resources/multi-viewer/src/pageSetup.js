/**
 * @file pageSetup.js is the root file for this app
 * @author Tammy DiPrima
 *
 * @param {string} divId - Main div id.
 * @param {object} images - Items to be displayed in viewer.
 * @param {number} numViewers - Total number of viewers.
 * @param {number} rows - LAYOUT: Number of rows (of viewers)
 * @param {number} columns - LAYOUT: Number of columns (of viewers)
 * @param {number} width - Viewer width
 * @param {number} height - Viewer height
 * @param {object} opts - Multi-viewer options; paintbrush, etc.
 */
const pageSetup = (divId, images, numViewers, rows, columns, width, height, opts) => {
  //console.clear();
  /*
  When the 'images' parameter becomes an array with null elements,
  it usually means that the session timed out or is in the process of timeout.
  So log the user out and have them start again.
   */
  let viewers = []; // eslint-disable-line prefer-const
  if (!isRealValue(images) || images[0] === null) {
    // logout & redirect
    document.write(
      "<script>window.alert('Click OK to continue...');window.location=`${window.location.origin}/auth/realms/Halcyon/protocol/openid-connect/logout?redirect_uri=${window.location.origin}`;</script>",
    );
  }

  document.addEventListener('DOMContentLoaded', setUp);
  window.addEventListener('keydown', hotKeysHandler);

  function setUp() {
    new Promise(resolve => {
      return resolve(opts);
    })
      .then(opts => {
        document.body.classList.add('theme--default');
        // dark-mode
        const modeToggle = e('i', { class: 'fas fa-moon moon' });

        // Slide name
        let name;
        const slide = images[0][0].location; // layer 0 location
        if (slide.includes('TCGA')) {
          const str = slide.match(/TCGA-[^%.]+/)[0];
          name = `Slide: ${str}`;
        } else {
          const arr = slide.split('/');
          name = `Slide: ${arr[arr.length - 1]}`;
        }

        const top = e('div', { style: 'display: flex' }, [
          e('div', { style: 'flex: 1' }, [modeToggle]),
          e('div', { style: 'flex: 1; text-align: right;' }, [name])
        ]);

        const referenceNode = document.querySelector(`#${divId}`);
        referenceNode.before(top);

        const getMode = localStorage.getItem('mode');
        if (getMode && getMode === 'dark-mode') {
          toggleButton(document.body, "theme--default", "theme--dark");
        }

        // toggle dark and light mode
        modeToggle.addEventListener('click', () => {
          toggleButton(modeToggle, "fa-sun", "fa-moon");
          toggleButton(modeToggle, "sun", "moon");
          toggleButton(document.body, "theme--default", "theme--dark");

          // Keep user's selected mode even on page refresh
          if (!document.body.classList.contains('theme--dark')) {
            localStorage.setItem('mode', 'light-mode');
          } else {
            localStorage.setItem('mode', 'dark-mode');
          }
        });

        // CREATE TABLE FOR VIEWERS
        const mainDiv = document.getElementById(divId);
        const table = e('table');
        mainDiv.appendChild(table); // TABLE ADDED TO PAGE

        // CREATE ROWS & COLUMNS
        let r;
        const num = rows * columns;
        let count = 0;
        for (r = 0; r < rows; r++) {
          const tr = table.insertRow(r);
          let c;
          for (c = 0; c < columns; c++) {
            const td = tr.insertCell(c);
            const osdId = createId(11); // DIV ID REQUIRED FOR OSD
            // CREATE DIV WITH CONTROLS, RANGE SLIDERS, BUTTONS, AND VIEWER.
            const idx = count;
            count++;

            if (numViewers < num && count - 1 === numViewers) {
              // we've done our last viewer
              break;
            }

            const container = e('div', { class: 'divSquare' });
            container.style.width = `${width}px`;
            td.appendChild(container); // ADD CONTAINER TO CELL

            let htm = '';

            // NAVIGATION TOOLS
            if (numViewers > 1) {
              htm += `<input type="checkbox" id="chkPan${idx}" checked=""><label for="chkPan${idx}">Match Pan</label>&nbsp;
<input type="checkbox" id="chkZoom${idx}" checked=""><label for="chkZoom${idx}">Match Zoom</label>&nbsp;&nbsp;`;
            }

            if (opts && opts.toolbarOn) {
              htm += `<div class="controls showDiv" id="hideTools${idx}"><div id="tools${idx}" class="showHover">`;

              // ANNOTATION TOOLS
              htm += '<div class="floated">';

              if (opts && opts.paintbrushColor) {
                htm += `<mark id="mark${idx}">${opts.paintbrushColor}</mark>&nbsp;`;
              } else {
                htm += `<mark id="mark${idx}">#00f</mark>&nbsp;`;
              }

              htm += `<button id="btnDraw${idx}" class="annotationBtn" title="Draw"><i class="fas fa-pencil-alt"></i></button>
<button id="btnEdit${idx}" class="annotationBtn" title="Edit"><i class="fas fa-draw-polygon"></i></button>
<!--<button id="btnAnnotate${idx}" class="annotationBtn" title="Add Annotation"><i class="fas fa-sticky-note"></i></button>-->
<button id="btnGrid${idx}" class="annotationBtn" title="Grid"><i class="fas fa-border-all"></i></button>
<button id="btnGridMarker${idx}" class="annotationBtn" title="Mark grid"><i class="fas fa-paint-brush"></i></button>
<button id="btnRuler${idx}" class="annotationBtn" title="Measure in microns"><i class="fas fa-ruler"></i></button>
<button id="btnShare${idx}" class="annotationBtn" title="Share this link"><i class="fas fa-share-alt"></i></button>
<button id="btnCam${idx}" class="annotationBtn" title="Snapshot"><i class="fas fa-camera"></i></button>
<button id="btnBlender${idx}" class="annotationBtn" title="Blend-modes"><i class="fas fa-blender"></i></button>
<button id="btnCrosshairs${idx}" class="annotationBtn" title="Crosshairs"><i class="fas fa-crosshairs"></i></button>
<button id="btnSave${idx}" class="annotationBtn" title="Save"><i class="fas fa-save"></i></button>
<div class="mag" style="display: inline">
  <button class="annotationBtn">
  <i class="fas fa-search"></i>
  </button>
  <!-- data-value = image zoom -->
  <div class="mag-content">
    <a href="#" data-value="0.025" id="1">1x</a>
    <a href="#" data-value="0.05" id="2">2x</a>
    <a href="#" data-value="0.1" id="4">4x</a>
    <a href="#" data-value="0.25" id="10">10x</a>
    <a href="#" data-value="0.5" id="20">20x</a>
    <a href="#" data-value="1.0" id="40">40x</a>
  </div>
</div>
<button id="btnMapMarker" class="annotationBtn" style="display: none"><i class="fas fa-map-marker-alt"></i> Hide markers</button>
</div>`;

              // END
              htm += '</div></div>';
            }

            // CREATE VIEWER
            htm += `<table><tr><td><div id="${osdId}" class="viewer dropzone" style="width: ${width}px; height: ${height}px;"></div>
</td><td id="layersAndColors${idx}" style="vertical-align: top;"></td>
</tr></table>`;

            // ADD VIEWER & WIDGETS TO CONTAINING DIV
            container.innerHTML = htm;

            // DRAW POLYGON COLOR PICKER
            const colorPicker = new CP(document.getElementById(`mark${idx}`));
            colorPicker.on('change', function(r, g, b, a) {
              this.source.value = this.color(r, g, b, a);
              this.source.innerHTML = this.color(r, g, b, a);
              this.source.style.backgroundColor = this.color(r, g, b, a);
            });

            const vInfo = { idx, osdId, layers: images[idx] };
            // Create MultiViewer object and add to array
            viewers.push(new MultiViewer(vInfo, numViewers, opts));
          }
        }

        return viewers;
      })
      .then(viewers => {
        // PAN/ZOOM CONTROLLER - accepts array of MultiViewers
        synchronizeViewers(viewers);
      });
  }

  // Hot keys
  function hotKeysHandler(evt) {
    const key = evt.key.toLocaleLowerCase();

    // esc: means 'Forget what I said I wanted to do!'; 'Clear'.
    if (key === 'escape' || key === 'esc') {
      evt.preventDefault();
      // Button-reset
      const buttons = document.getElementsByClassName('btnOn');
      for (let i = 0; i < buttons.length; i++) {
        buttons[i].click();
      }
    }

    // control-r for 'ruler'
    if (evt.ctrlKey && key === 'r') {
      evt.preventDefault();
      for (let i = 0; i < viewers.length; i++) {
        document.querySelectorAll('[id^="btnRuler"]').forEach(node => {
          node.click();
        });
      }
    }

  }
};
