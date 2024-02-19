import { createButton } from "../helpers/elements.js";
import { SelectionHelper } from "../interactive/SelectionHelper.js";

export function selection(renderer, controls) {
  let button = createButton({
    id: "selection",
    innerHtml: "<i class=\"fa fa-bar-chart\"></i>",
    title: "select for algorithm"
  });

  let isSelectionEnabled = false; // Track selection state
  let selectionBox = null; // Hold the selection box instance

  function toggleSelection() {
    if (isSelectionEnabled) {
      // Disable selection
      if (selectionBox) {
        selectionBox.enabled = false;
        selectionBox.element.style.display = 'none';
        selectionBox.dispose();
        selectionBox = null;
      }
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
    } else {
      // Enable selection
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');

      selectionBox = new SelectionHelper(renderer, "selectBox");
      selectionBox.onSelectOver = () => {
        let data = renderer.domElement.toDataURL("image/png");
        let img = new Image();
        img.onload = () => {
          let sb = selectionBox.element.style;
          let c = document.createElement("canvas");
          c.width = parseInt(sb.width);
          c.height = parseInt(sb.height);
          let ctx = c.getContext("2d");

          // Calculate offsets
          let rect = renderer.domElement.getBoundingClientRect();
          let offsetX = rect.left + window.scrollX; // pageXOffset
          let offsetY = rect.top + window.scrollY; // pageYOffset

          ctx.drawImage(
            img,
            parseInt(sb.left) - offsetX,
            parseInt(sb.top) - offsetY,
            parseInt(sb.width),
            parseInt(sb.height),
            0,
            0,
            parseInt(sb.width),
            parseInt(sb.height)
          );
          let cData = c.toDataURL("image/png");
          window.open(cData, "_blank");

          const imageData = ctx.getImageData(0, 0, c.width, c.height);
          const pixels = imageData.data;
          console.log(pixels);
        };
        img.src = data;
        selectionBox.element.parentElement.removeChild(selectionBox.element);
      };
    }
    isSelectionEnabled = !isSelectionEnabled; // Toggle the state
  }

  button.addEventListener("click", toggleSelection);
}
