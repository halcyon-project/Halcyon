import {
    SRGBColorSpace,
    Object3D,
    LOD,
    Group,
    Shape,
    ShapeGeometry,
    MeshBasicMaterial,
    DoubleSide,
    Mesh,
    Line,
    BufferGeometry,
    LineBasicMaterial,
    EdgesGeometry,
    LineSegments,
    Vector3,
    LinearFilter,
    Box3,
    Sprite,
    SpriteMaterial,
    CanvasTexture,    
    Texture,
    ShaderMaterial,
    Color,
    RepeatWrapping,
    ClampToEdgeWrapping,
    NearestFilter
} from 'three';

import { tileLoader } from './TileLoader.js';

export const TileSize = 512;

function isValidImageInfo(data) {
    return data
        && Number.isFinite(data.width) && data.width > 0
        && Number.isFinite(data.height) && data.height > 0
        && Array.isArray(data.tiles) && data.tiles[0]
        && Number.isFinite(data.tiles[0].width) && Number.isFinite(data.tiles[0].height);
}

function showViewerError(message) {
    console.error(message);
    let div = document.getElementById('zephyr-error');
    if (!div) {
        div = document.createElement('div');
        div.id = 'zephyr-error';
        div.style.cssText = 'position:fixed;top:10px;left:50%;transform:translateX(-50%);'
            + 'z-index:1000;background:#b00020;color:#fff;padding:10px 16px;border-radius:4px;'
            + 'font:14px sans-serif;max-width:80%;box-shadow:0 2px 8px rgba(0,0,0,0.4);';
        document.body.appendChild(div);
    }
    div.textContent = message;
}

function srcurl(src, x, y, w, h, tilex, tiley, scale, name) {
    const a = Math.trunc(w);
    const b = Math.trunc(h);
    const m = Math.trunc(Math.round(w * scale));
    const n = Math.trunc(Math.round(h * scale));
    if ((w < 1) || (h < 1)) {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = TileSize;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = 'green';
        ctx.fillRect(0, 0, TileSize, TileSize);
        ctx.clearRect(0, 0, TileSize, TileSize);
        const newTexture = new Texture(canvas);
        newTexture.needsUpdate = true;
        return newTexture;
    } else {
        const ha = `/iiif/?iiif=${src}/${x},${y},${a},${b}/!${m},${n}/0/default.png`;
        return tileLoader.load(ha, (texture) => {
            texture.wrapS = ClampToEdgeWrapping;
            texture.wrapT = ClampToEdgeWrapping;
            texture.minFilter = NearestFilter;
            texture.magFilter = NearestFilter;
            texture.colorSpace = SRGBColorSpace;
            texture.generateMipmaps = false;
            const wratio = tilex / texture.image.width;
            const hratio = tiley / texture.image.height;
            texture.repeat.set(wratio, hratio);
            texture.offset.set(0, 1 - hratio);
            texture.needsUpdate = true;
        });
    }
}

function Square(renderer, src, offset, name) {
    var texture = src;
    //texture.magFilter = LinearFilter;
    //texture.minFilter = LinearFilter;
    //texture.generateMipmaps = false;
    //var maxAnisotropy = renderer.capabilities.anisotropy;
    //texture.anisotropy = maxAnisotropy;
    texture.colorSpace = SRGBColorSpace;
    const square = new Shape();
    square.moveTo(0, 0);
    square.lineTo(0, 1);
    square.lineTo(1, 1);
    square.lineTo(1, 0);
    const geometry = new ShapeGeometry(square);
    geometry.center();
    const textureMaterial = new MeshBasicMaterial({map: texture, depthWrite: true, side: DoubleSide});

    /*
    var material;
    const label = name.slice(-2);
    switch(label) {
        case "NW": material = new MeshBasicMaterial({ color: 0xff00ff }); break;
        case "NE": material = new MeshBasicMaterial({ color: 0x00ff00 }); break;
        case "SW": material = new MeshBasicMaterial({ color: 0x0000ff }); break;
        case "SE": material = new MeshBasicMaterial({ color: 0xffff00 }); break;
        default:   material = new MeshBasicMaterial({ color: 0xffffff }); break;
    }*/
    texture.colorSpace = SRGBColorSpace;
    const X = new Mesh(geometry, textureMaterial);
    //X.scale.x = 1;
    //X.scale.y = 1;
    X.frustumCulled = false;
    X.position.set(0, 0, offset);
    return X;
}

function DrawAxis(scene) {
    const material = new LineBasicMaterial({ color: 0x0000ff });
    const points = [];
    points.push(new Vector3(-200000, 0, 5));
    points.push(new Vector3(200000, 0, 5));
    const geometry = new BufferGeometry().setFromPoints(points);
    const line = new Line(geometry, material);
    scene.add(line);
    const points2 = [];
    points2.push(new Vector3(0, -200000, 5));
    points2.push(new Vector3(0, 200000, 5));
    const geometry2 = new BufferGeometry().setFromPoints(points2);
    const line2 = new Line(geometry2, material);
    scene.add(line2);
}

function getRandomNumberBetween(a, b) {
    return Math.random() * (b - a) + a;
}

function CreateStackViewer(renderer, scene, urls, offset) {
    console.log("CreateStackViewer : "+urls+" offset -> "+offset);
    var stackviewer = new StackViewer();
    var off = offset;
    urls.forEach((url) => {
        console.log(url);        
        AddImageViewer(stackviewer, url, off);
        off = off + 2000;
    });
    scene.add(stackviewer);
    stackviewer.position.x = 5000;
    stackviewer.position.y = 5000;
    stackviewer.position.z = 5000;
}

function AddImageViewer(stackviewer, url, offset) {
  console.log("AddImageViewer Xc : "+url+" offset -> "+offset);
  var target = "/iiif/?iiif=" + url + "/info.json";
  fetch(target)
    .then(response => response.json())
    .then(data => {
      const x = 0;
      const y = 0;
      const w = data.width;
      const h = data.height;
      const tilex = data.tiles[0].width;
      const tiley = data.tiles[0].height;
      const lod = new ImageViewer(null, url, w, h, x, y, w, h, tilex, tiley, 0, data, 0, "ROOT", 0, 0);
      lod.name = "ImageViewer";
      lod.imageWidth = w;
      lod.imageHeight = h;
      lod.url = url;
      lod.offset = offset;
      lod.frustumCulled = false;
      lod.scale.x = w;
      lod.scale.y = h;
      stackviewer.addLayer(lod);
      lod.position.z = offset;
    }).catch(error => console.error('Error fetching data:', error));
}

/**
 * Renders a single IIIF image as a tiled level-of-detail pyramid.
 * `url` must be a BARE IIIF identifier (e.g. the image subject URI): this
 * function prepends the `/iiif/?iiif=` service prefix itself, matching how
 * FeatureManager builds `/iiif/?iiif={id}/info.json`. Do NOT pass an already
 * service-wrapped URL (e.g. PathFinder.LocalPath2IIIFURL) — that double-wraps.
 */
function CreateImageViewer(renderer, scene, url, offset) {
    const target = "/iiif/?iiif=" + url + "/info.json";
    fetch(target)
        .then(response => {
            if (!response.ok) {
                throw new Error(`IIIF info request failed (${response.status})`);
            }
            return response.json();
        })
        .then(data => {
            if (!isValidImageInfo(data)) {
                showViewerError(`Image metadata is missing or malformed for ${url}`);
                return;
            }
            const x = 0;
            const y = 0;
            const w = data.width;
            const h = data.height;
            const tilex = data.tiles[0].width;
            const tiley = data.tiles[0].height;
            const lod = new ImageViewer(renderer, url, w, h, x, y, w, h, tilex, tiley, offset, data, 0, "ROOT", 0, 0);
            lod.imageWidth = w;
            lod.imageHeight = h;
            lod.url = url;
            lod.frustumCulled = false;
            lod.scale.x = w;
            lod.scale.y = h;
            scene.add(lod);
        })
        .catch(error => showViewerError(`Error loading image ${url}: ${error.message}`));
}

class ImageViewer extends LOD {
    constructor(renderer, url, width, height, x, y, w, h, tilex, tiley, offset, info, level, name, a, b) {
        super();
        //if (name.startsWith("ROOT/SE/")) console.log("ImageViewer( "+name +" "+width+" "+height+" "+tilex+" "+tiley+" "+offset+" "+level+" "+a+" "+b+")");
        this.isImageViewer = true;
        this.type = 'ImageViewer';
        this.name = 'ImageViewer';
        this.booted = false;
        this.level = level;
        this.a = a;
        this.b = b;
        const numtilesx = Math.pow(2, level);
        const numtilesy = Math.pow(2, level);
        const tw = 2 * tilex * Math.ceil(width / (2 * tilex)) / numtilesx;
        const th = 2 * tiley * Math.ceil(height / (2 * tiley)) / numtilesy;
        const ts = Math.round(Math.max(tw, th));
        this.shrink = tilex / ts;
        const ttw = ((a * ts + ts) > width) ? ts - ((a * ts + ts) - width) : ts;
        const tth = ((b * ts + ts) > height) ? ts - ((b * ts + ts) - height) : ts;
        const low = Square(renderer, srcurl(url, a * ts, b * ts, ttw, tth, tilex, tiley, this.shrink, name), offset, name);
        low.name = "Square";
        low.frustumCulled = true;
        this.edistance = width / Math.pow(2, level);
        this.addLevel(low, this.edistance);
        low.onBeforeRender = () => {
            //if ((this.level < 3)&&(!this.booted)) {
            //if (ts >= tilex) {
            if (!this.booted) {
                this.booted = true;
                const nextlevel = level + 1;
                const high = new Group();
                const nw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/NW", 2 * this.a, 2 * this.b);
                nw.position.set(-0.25, 0.25, 0);
                nw.scale.x = 0.5;
                nw.scale.y = 0.5;
                high.add(nw);
                if (ttw / 2 > 1) {
                    const ne = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/NE", 2 * this.a + 1, 2 * this.b);
                    ne.position.set(0.25, 0.25, 0);
                    ne.scale.x = 0.5;
                    ne.scale.y = 0.5;
                    high.add(ne);
                }
                if (tth / 2 > 1) {
                    const sw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/SW", 2 * this.a, 2 * this.b + 1);
                    sw.position.set(-0.25, -0.25, 0);
                    sw.scale.x = 0.5;
                    sw.scale.y = 0.5;
                    high.add(sw);
                }
                if ((ttw / 2 > 1) && (tth / 2 > 1)) {
                    const se = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/SE", 2 * this.a + 1, 2 * this.b + 1);
                    se.position.set(0.25, -0.25, 0);
                    se.scale.x = 0.5;
                    se.scale.y = 0.5;
                    high.add(se);
                }
                high.frustumCulled = true;
                if (ts <= 2 * tilex) {
                    this.addLevel(high, 0);
                    this.bottom = true;
                    //console.log("ADD LEVEL : ZERO");
                } else {
                    const sigh = 0.25 * this.edistance;
                    this.addLevel(high, sigh);
                    // console.log("ADD LEVEL : "+sigh);
                }
            }
        };
    }

    update(camera) {
        super.update(camera);
        let currentLevelIndex = -1;
        this.levels.forEach((level, index) => {
            if (level.object.visible) {
                currentLevelIndex = index;
            }
        });
        if (currentLevelIndex !== -1) {
            //  console.log(this.level+ ` Current LOD level: ${currentLevelIndex}`);
        } else {
            //  console.log(this.level+ "No LOD level is currently visible.");
        }
    }
}

function addXAxis() {
    const points = [
        new Vector3(-100000, 0, 0),
        new Vector3(100000, 0, 0)
    ];    
    const geometry = new BufferGeometry().setFromPoints(points);
    const material = new LineBasicMaterial({ color: 0x00ff00 });
    const line = new Line(geometry, material);
    const label = MakeText("X");   
    label.position.x = 20000;    
    line.add(label);
    return line;
}

function addYAxis() {
    const points = [
        new Vector3(0, -100000, 0),
        new Vector3(0, 100000, 0)
    ];    
    const geometry = new BufferGeometry().setFromPoints(points);
    const material = new LineBasicMaterial({ color: 0x00ff00 });
    const line = new Line(geometry, material);
    const label = MakeText("Y");   
    label.position.y = 20000;    
    line.add(label);    
    return line;
}

function addZAxis() {
    const points = [
        new Vector3(0, 0, -100000),
        new Vector3(0, 0, 100000)
    ];    
    const geometry = new BufferGeometry().setFromPoints(points);
    const material = new LineBasicMaterial({ color: 0x00ff00 });
    const line = new Line(geometry, material);
    const label = MakeText("Z");
    label.position.z = 20000;    
    line.add(label);
    return line;
}

function MakeText(text) {
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d');
    context.font = 'Bold 48px Arial';
    context.fillStyle = 'white';
    context.fillText(text, 50, 50);
    const texture = new CanvasTexture(canvas);
    const material = new SpriteMaterial({ map: texture });
    const sprite = new Sprite(material);
    sprite.scale.set(10000, 10000, 10000); // Adjust size
    return sprite;
}

class Stack extends Object3D {
    
    elayers = [];
    
    createUX() {
        let myDiv = document.createElement("div");
        myDiv.style.width = '100%';
        myDiv.style.color = 'lightblue';
        myDiv.style.margin = '0';
        let canvas = document.querySelector('canvas');
        document.body.insertBefore(myDiv, canvas);                
        let slider = document.createElement("input");
        slider.id = "slider123";
        slider.type = "range";
        slider.min = "1";
        slider.value = 10;
        slider.max = "100";
        this.offset = 0;
        slider.classList.add("annotationBtn");
        slider.addEventListener('input', (event) => {
            console.log(`Final value selected: ${event.target.value}`);
            this.scale.z = (event.target.value / 10);
        });    
        myDiv.appendChild(slider);
        console.log("StackViewer ID : "+this.id);
    }
    
    constructor(we, statement) {
        super();
        this.store = we.getStore();        
        this.type = 'StackViewer';
        this.spacing = 1.0;        
        this.createUX();
        this.add(addXAxis());
        this.add(addYAxis());
        this.add(addZAxis());
        //this.add(MakeText('XYZ'));        
        this.ListImages(statement.subject);
    }
    
    setSpacing( value ) {
        this.spacing = value;
    }
    
    addLayer( object ) {
        this.elayers.push( object );
        this.add( object );
    }

    ListImages(subject) {
        console.log("LIST IMAGES ==========================");
        const zeph = $rdf.Namespace('https://halcyon.is/zephyr/ns/');
        const layers = this.store.match(subject, zeph('layers'), null);
        const layerList = layers[0].object.elements;
        layerList.forEach(layerName => {
            const image = this.store.match(layerName, zeph('src'), null);
            const ii = image[0].object.value;
            console.log("Image : "+ ii);
            AddImageViewer(this, ii, this.offset);
            this.offset = this.offset + 2000;
        });
    }
}

export { Square, CreateImageViewer, CreateStackViewer, DrawAxis, Stack };
