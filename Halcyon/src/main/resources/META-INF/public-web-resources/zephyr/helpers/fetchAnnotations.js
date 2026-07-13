import { createButton } from "./elements.js";
import { getUrl } from "./conversions.js";
import { loadAnnotationSetInto } from "./save.js";
import { getAnnotationLabel, setAnnotationLabel } from "./sparql.js";
import { activeImageUrl, getActiveEntry, createAnnotationLayer } from "./annotationTarget.js";
import { getRegistry } from "../context.js";
import { invalidate } from "../renderLoop.js";

export function fetchAnnotations(scene) {
  const button = createButton({
    id: "fetchAnnotations",
    innerHtml: "<i class=\"fas fa-comment-alt\"></i>",
    title: "Fetch Annotations"
  });

  let objectMap = new Map();

  button.addEventListener('click', () => {
    const annotationsDiv = document.getElementById("annotations-div");

    if (annotationsDiv) {
      // Toggle visibility
      if (annotationsDiv.style.display === "none") {
        annotationsDiv.style.display = "block";
      } else {
        annotationsDiv.style.display = "none";
      }
    } else {
      // Create and show the div. Fetch the annotation sets attached to the
      // active layer's image (the layer selected in the Layers panel).
      const imageId = activeImageUrl() || getUrl(scene);
      if (imageId) {
        fetchA(imageId).then(annotationArray => {
          if (annotationArray && annotationArray.length > 0) {
            displayPopup(annotationArray);
          }
        });
      } else {
        alert('Please select a layer (or wait for the image to load), then try again.');
      }
    }
  });

  // Function to fetch data and return the annotation array
  async function fetchA(url) {
    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'application/ld+json',
          'Prefer': 'return=representation; shacl="https://halcyon.is/ns/AnnotationsShape"; include=https://halcyon.is/ns/annotation'
        }
      });

      // The auth layer redirects unauthenticated /lws requests to the
      // sign-in page; fetch follows it silently and hands us HTML.
      if (response.redirected) {
        console.error('Annotation list redirected (not signed in):', response.url);
        alert('Not signed in: the server redirected to the sign-in page.\n'
          + `Sign in to Halcyon (open ${window.location.origin}/ in this browser), then try again.`);
        return [];
      }

      // Check if the response is OK and has content
      if (!response.ok) {
        console.error(`HTTP error! status: ${response.status}`);
        alert(`HTTP error! status: ${response.status}`);
        return [];
      }

      const responseText = await response.text();
      // console.log('Raw response text:', responseText);

      // If response text is empty, alert the user and print the URL
      if (!responseText) {
        console.error('Response text is empty. URL:', url);
        alert(`Error: Response text is empty. URL: ${url}`);
        return [];
      }

      // Try parsing the JSON
      let data;
      try {
        data = JSON.parse(responseText);
      } catch (e) {
        console.error('Error parsing JSON:', e, responseText.slice(0, 200));
        alert('Error parsing the annotation list: the server returned a non-JSON '
          + 'response. This usually means you are not signed in to Halcyon.');
        return [];
      }

      if (!data.annotation) {
        // console.log('No annotations:', JSON.stringify(data));
        alert('No annotations yet. Please create, then save.');
        return [];
      }

      return data.annotation;
    } catch (error) {
      console.error('Error:', error);
      alert('Error fetching annotations');
      return [];
    }
  }

  async function displayPopup(annotationArray) {
    const div = document.createElement('div');
    div.id = "annotations-div";
    div.classList.add("floating-div");
    document.body.appendChild(div);

    // Create a draggable header for the div
    const dragHandle = document.createElement('div');
    dragHandle.classList.add('drag-handle');
    dragHandle.innerHTML = "<strong>Annotation Sets:</strong>";
    div.appendChild(dragHandle);

    const hint = document.createElement('div');
    hint.style.cssText = 'font-size:11px;color:#555;margin:2px 0 6px 0;max-width:340px;white-space:normal;';
    hint.textContent = 'Check a set to display it as its own named layer on the active '
      + 'source; uncheck to hide it. Use "Save annotations" (or Save Stack) to persist '
      + 'the layers you have drawn.';
    div.appendChild(hint);

    // Create and style the close button
    const closeButton = document.createElement('span');
    closeButton.innerHTML = '&times;';
    closeButton.classList.add('close-button');

    // Add click event to hide the div
    closeButton.addEventListener('click', () => {
      div.style.display = 'none';
    });

    dragHandle.appendChild(closeButton); // Add close button to drag handle

    // Iterate through the annotations
    let firstCheckbox = null;
    for (let annotation of annotationArray) {
      // Create checkbox
      const label = document.createElement('label');
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.value = annotation;
      if (!firstCheckbox) firstCheckbox = checkbox;

      label.appendChild(checkbox);

      // Create text input for name. One failed lookup (offline, not signed
      // in) must not abort the rest of the list — fall back to the filename.
      let name = null;
      try {
        name = await getAnnotationLabel(annotation);
      } catch (error) {
        console.error('Annotation label lookup failed:', annotation, error);
      }
      const textInput = document.createElement('input');
      textInput.type = 'text';
      // Use annotation label or filename
      textInput.value = name ? name : annotation.split("/").pop();
      label.appendChild(textInput);

      const updateButton = document.createElement('button');
      updateButton.innerText = 'Rename';
      updateButton.title = "Update Annotation Label";
      updateButton.addEventListener('click', () => {
        setAnnotationLabel(annotation, textInput.value);
      });
      label.appendChild(updateButton);

      // Create text node for name
      // if (name) {
      //   label.appendChild(document.createTextNode(name));
      // } else {
      //   let sections = annotation.split("/");
      //   label.appendChild(document.createTextNode(sections[sections.length - 1]));
      // }

      div.appendChild(label);
      div.appendChild(document.createElement('br'));

      // Checking a set loads it as its OWN named annotation layer (nested under
      // the active source in the panel); unchecking removes that layer. The
      // set's file on the server is kept either way. objectMap maps set URL ->
      // annotation-layer id.
      checkbox.addEventListener('change', function () {
        const r = getRegistry();
        if (this.checked) {
          if (!objectMap.has(annotation)) {
            const source = getActiveEntry();
            if (!source) { alert('Select a layer first, then check a set.'); this.checked = false; return; }
            const setName = (textInput && textInput.value) || annotation.split('/').pop();
            const ae = createAnnotationLayer(source, setName, false);
            if (!ae) { alert('Select/load the image first, then check a set.'); this.checked = false; return; }
            ae.src = annotation;   // its content already lives at this LDP URL
            objectMap.set(annotation, ae.id);
            loadAnnotationSetInto(annotation, ae.object3d)
              .then(() => invalidate())
              .catch(error => {
                alert('Could not display this annotation set: ' + error.message);
                if (r) r.remove(ae.id);
                objectMap.delete(annotation);
                this.checked = false;
              });
          } else if (r) {
            r.setVisible(objectMap.get(annotation), true);
          }
        } else if (objectMap.has(annotation)) {
          if (r) r.remove(objectMap.get(annotation));
          objectMap.delete(annotation);
          invalidate();
        }
      });
    }

    // Make the div draggable by the drag handle
    dragElement(div, dragHandle);

    // A single set is what the user came for — display it immediately
    // instead of waiting for the (easily missed) checkbox.
    if (annotationArray.length === 1 && firstCheckbox) {
      firstCheckbox.click();
    }
  }

  function dragElement(element, handle) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    handle = handle || element;

    handle.addEventListener('mousedown', dragMouseDown);

    function dragMouseDown(e) {
      e.preventDefault();
      pos3 = e.clientX;
      pos4 = e.clientY;
      document.addEventListener('mouseup', closeDragElement);
      document.addEventListener('mousemove', elementDrag);
    }

    function elementDrag(e) {
      e.preventDefault();
      pos1 = pos3 - e.clientX;
      pos2 = pos4 - e.clientY;
      pos3 = e.clientX;
      pos4 = e.clientY;
      element.style.top = (element.offsetTop - pos2) + "px";
      element.style.left = (element.offsetLeft - pos1) + "px";
    }

    function closeDragElement() {
      document.removeEventListener('mouseup', closeDragElement);
      document.removeEventListener('mousemove', elementDrag);
    }
  }
}
