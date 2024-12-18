// This script exports a function to fetch, display, and manage annotations (including 
// drag, show, hide, rename operations) for a given scene from a provided URL, with the 
// results encapsulated in a draggable UI pop-up window.
import { createButton } from "./elements.js";
import { getUrl } from "./conversions.js";
import { deserializeScene } from "./save.js";
import { getAnnotationLabel, setAnnotationLabel } from "./sparql.js";

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
      // Create and show the div
      const url = getUrl(scene);
      if (url) {
        const parts = url.split("?iiif=");
        const annotationUrl = parts[1];

        fetchA(annotationUrl).then(annotationArray => {
          if (annotationArray && annotationArray.length > 0) {
            displayPopup(annotationArray);
          }
        });
      } else {
        alert('Please wait for image to load. Then try again.');
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
        console.error('Error parsing JSON:', e);
        alert('Error parsing JSON');
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
    for (let annotation of annotationArray) {
      // Create checkbox
      const label = document.createElement('label');
      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.value = annotation;

      label.appendChild(checkbox);

      // Create text input for name
      let name = await getAnnotationLabel(annotation);
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

      checkbox.addEventListener('change', function () {
        if (this.checked) {
          // If the checkbox is selected, fetch the annotations again
          if (!objectMap.has(annotation)) {
            fetch(annotation)
              .then(response => {
                if (!response.ok) {
                  throw new Error('URL does not exist');
                }
                return response.json();
              })
              .then(data => {
                try {
                  const objects = deserializeScene(scene, data);
                  objectMap.set(annotation, objects);
                } catch (e) {
                  console.error('Deserialization error:', e);
                }
              })
              .catch(error => alert(error.message));
          } else {
            // If the objects are already fetched, make them visible again
            objectMap.get(annotation).forEach(obj => {
              obj.visible = true;
            });
          }
        } else {
          // If the checkbox is deselected, remove the objects from the scene
          if (objectMap.has(annotation)) {
            objectMap.get(annotation).forEach(obj => {
              if (obj.parent) {
                obj.parent.remove(obj); // Remove from parent
              } else {
                scene.remove(obj); // Fallback to remove directly from the scene
              }
              if (obj.geometry) obj.geometry.dispose(); // Dispose of geometry
              if (obj.material) {
                // Dispose of material (handle arrays of materials)
                if (Array.isArray(obj.material)) {
                  obj.material.forEach(material => material.dispose());
                } else {
                  obj.material.dispose();
                }
              }
            });
            objectMap.delete(annotation); // Remove from the objectMap
          }
        }
      });
    }

    // Make the div draggable by the drag handle
    dragElement(div, dragHandle);
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
