import * as THREE from 'three';
import { createButton } from "../helpers/button.js"

export function shapes(scene, camera, renderer, controls) {

  let isDrawing = false;
  let mouseIsPressed = false;

  let rectangleButton = createButton({
    id: "rectangle",
    innerHtml: "<i class=\"fa-regular fa-square\"></i>",
    title: "rectangle"
  });

  let ellipseButton = createButton({
    id: "ellipse",
    innerHtml: "<i class=\"fa-regular fa-circle\"></i>",
    title: "ellipse"
  });

  let polygonButton = createButton({
    id: "polygon",
    innerHtml: "<i class=\"fa-solid fa-draw-polygon\"></i>",
    title: "polygon"
  });

  class Shape {
    constructor(mesh) {
      this.mesh = mesh;
      this.startPoint = new THREE.Vector2(0, 0);
      this.endPoint = new THREE.Vector2(0, 0);
      this.points = [];
    }

    onMouseDown(event) {}
    onMouseMove(event) {}
    onMouseUp(event) {}
    onDoubleClick(event) {}
    update() {}
  }

  class Rectangle extends Shape {
    constructor(mesh) {
      super(mesh);
      // Initialize the geometry with four vertices
      this.mesh.geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(4 * 3), 3));
    }

    onMouseDown(event) {
      if (isDrawing) {
        mouseIsPressed = true;
        this.startPoint = getMousePosition(event.clientX, event.clientY);
      }
    }

    onMouseMove(event) {
      if (isDrawing && mouseIsPressed) {
        this.endPoint = getMousePosition(event.clientX, event.clientY);
        this.update();
      }
    }

    onMouseUp(event) {
      if (isDrawing) {
        mouseIsPressed = false;
        this.update();
      }
    }

    update() {
      let positions = this.mesh.geometry.attributes.position.array;
      positions[0] = this.startPoint.x;
      positions[1] = this.startPoint.y;
      positions[2] = this.startPoint.z;
      positions[3] = this.endPoint.x;
      positions[4] = this.startPoint.y;
      positions[5] = this.startPoint.z;
      positions[6] = this.endPoint.x;
      positions[7] = this.endPoint.y;
      positions[8] = this.startPoint.z;
      positions[9] = this.startPoint.x;
      positions[10] = this.endPoint.y;
      positions[11] = this.startPoint.z;
      this.mesh.geometry.attributes.position.needsUpdate = true;
    }
  }

  class Ellipse extends Shape {
    constructor(mesh, segments = 64) {
      super(mesh);
      this.segments = segments;
      // Initialize the geometry with segments + 1 vertices
      this.mesh.geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array((segments + 1) * 3), 3));
    }

    onMouseDown(event) {
      if (isDrawing) {
        mouseIsPressed = true;
        this.startPoint = getMousePosition(event.clientX, event.clientY);
      }
    }

    onMouseMove(event) {
      if (isDrawing && mouseIsPressed) {
        this.endPoint = getMousePosition(event.clientX, event.clientY);
        this.update();
      }
    }

    onMouseUp(event) {
      if (isDrawing) {
        mouseIsPressed = false;
        this.update();
      }
    }

    update() {
      let positions = new Float32Array((this.segments + 1) * 3);
      let radiusX = Math.abs(this.startPoint.x - this.endPoint.x) / 2;
      let radiusY = Math.abs(this.startPoint.y - this.endPoint.y) / 2;
      let centerX = (this.startPoint.x + this.endPoint.x) / 2;
      let centerY = (this.startPoint.y + this.endPoint.y) / 2;

      for (let i = 0; i <= this.segments; i++) {
        let theta = (i / this.segments) * Math.PI * 2;
        positions[i * 3] = centerX + radiusX * Math.cos(theta);
        positions[i * 3 + 1] = centerY + radiusY * Math.sin(theta);
        positions[i * 3 + 2] = 0;
      }

      this.mesh.geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
      this.mesh.geometry.attributes.position.needsUpdate = true;
    }
  }

  class Polygon extends Shape {
    constructor(mesh) {
      super(mesh);
      this.points = [];
    }

    onMouseDown(event) {
      if (isDrawing) {
        mouseIsPressed = true;
      } else {
        this.points = [];
      }
      this.points.push(getMousePosition(event.clientX, event.clientY));
      this.update();
    }

    onMouseMove(event) {
      if (isDrawing && mouseIsPressed) {
        this.endPoint = getMousePosition(event.clientX, event.clientY);
        this.update();
      }
    }

    onMouseUp(event) {
      // Polygon continues drawing until double click
    }

    onDoubleClick(event) {
      if (isDrawing) {
        mouseIsPressed = false;
        this.update();
      }
    }

    update() {
      let numPoints = this.points.length;
      if (numPoints >= 2) {
        let positions = new Float32Array(numPoints * 3);
        for (let i = 0; i < numPoints; i++) {
          positions[i * 3] = this.points[i].x;
          positions[i * 3 + 1] = this.points[i].y;
          positions[i * 3 + 2] = this.points[i].z;
        }
        this.mesh.geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
        this.mesh.geometry.attributes.position.needsUpdate = true;
        this.mesh.geometry.setDrawRange(0, numPoints);
      }
    }
  }

  class ShapeFactory {
    constructor(mesh) {
      this.mesh = mesh;
    }

    createShape(type) {
      switch (type) {
        case "rectangle":
          return new Rectangle(this.mesh);
        case "ellipse":
          return new Ellipse(this.mesh);
        case "polygon":
          return new Polygon(this.mesh);
        default:
          throw new Error("Invalid shape type");
      }
    }
  }

  let material = new THREE.LineBasicMaterial({ color: 0x0000ff });
  let geometry = new THREE.BufferGeometry();
  let mesh = new THREE.LineLoop(geometry, material);
  mesh.renderOrder = 999;
  scene.add(mesh);

  let factory = new ShapeFactory(mesh, controls);
  let currentShape = null;

  function handler() {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
    } else {
      isDrawing = true;
      controls.enabled = false;
    }
    console.log("isDrawing:", isDrawing);
  }

  // Button event listeners
  rectangleButton.addEventListener("click", function () {
    currentShape = factory.createShape("rectangle");
    handler();
  });

  ellipseButton.addEventListener("click", function () {
    currentShape = factory.createShape("ellipse");
    handler();
  });

  polygonButton.addEventListener("click", function () {
    currentShape = factory.createShape("polygon");
    handler();
  });

  // Canvas event listeners
  renderer.domElement.addEventListener("mousedown", function (event) {
    if (currentShape) currentShape.onMouseDown(event);
  }, false);

  renderer.domElement.addEventListener("mousemove", function (event) {
    if (currentShape) currentShape.onMouseMove(event);
  }, false);

  renderer.domElement.addEventListener("mouseup", function (event) {
    if (currentShape) currentShape.onMouseUp(event);
  }, false);

  renderer.domElement.addEventListener("dblclick", function (event) {
    if (currentShape) currentShape.onDoubleClick(event);
  }, false);

  function getMousePosition(clientX, clientY) {
    let rect = renderer.domElement.getBoundingClientRect();
    let x = ((clientX - rect.left) / rect.width) * 2 - 1;
    let y = -((clientY - rect.top) / rect.height) * 2 + 1;

    let vector = new THREE.Vector3(x, y, 0.5);
    vector.unproject(camera);

    let dir = vector.sub(camera.position).normalize();
    let distance = -camera.position.z / dir.z;
    let pos = camera.position.clone().add(dir.multiplyScalar(distance));
    return pos;
  }
}
