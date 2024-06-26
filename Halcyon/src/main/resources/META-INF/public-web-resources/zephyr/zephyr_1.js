import {
  SRGBColorSpace,
  Object3D,
  LOD,
  Group,
  TextureLoader,
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
  FileLoader,
  LinearFilter
} from 'three';

// import {dumpObject} from './dumpObject.js';

function srcurl(src, x, y, w, h, tilex, tiley) {
  const ha = src + "/" + x + "," + y + "," + w + "," + h + "/" + tilex + ",/0/default.png";
  return new TextureLoader().load(ha);
}

function Square(renderer, x, y, w, h, src, offset) {
  var texture = src;
  // Set texture filtering
  texture.magFilter = LinearFilter;
  texture.minFilter = LinearFilter;
  // Disable mipmaps
  texture.generateMipmaps = false;

  // Enable anisotropic filtering
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
  const material = new MeshBasicMaterial({map: texture, depthWrite: false, side: DoubleSide, transparent: true});
  texture.colorSpace = SRGBColorSpace;
  const X = new Mesh(geometry, material);
  X.scale.x = w;
  X.scale.y = h;
  X.frustumCulled = false;
  X.position.set(0, 0, offset);
  return X;
}

function createPolygon(wkt, x, y) {
    const reader = new jsts.io.WKTReader();
    const geom = reader.read(wkt);
    if (geom.getGeometryType() !== "Polygon") {
        console.error("The provided WKT is not a polygon");
        return;
    }
    const coordinates = geom.getCoordinates();
    const points = coordinates.map(coord => new Vector3( coord.x - x, coord.y - y , 0 ));
    console.log(points);
    const shape = new Shape(points);
    const geometry = new ShapeGeometry(shape);
    const material = new MeshBasicMaterial({ color: 0xffff00, side: DoubleSide });
    return new Mesh(geometry, material);
}

function CreateImageViewer(renderer, scene, url, offset) {
  var target = url + "/info.json";
  fetch(target)
    .then(response => response.json())
    .then(data => {
      const x = 0;
      const y = 0;
      const w = data.width;
      const h = data.height;
      const offset = 0;
      const tilex = data.tiles[0].width;
      const tiley = data.tiles[0].height;
      // console.log("data:", data);
      const lod = new ImageViewer(renderer, url, x, y, w, h, tilex, tiley, offset, data, 0);
      lod.name = "ImageViewer";
      lod.imageWidth = w;
      lod.imageHeight = h;
      lod.url = url;
      lod.frustrumCulled = false;
      scene.add(lod);
      // console.log(`%c${dumpObject(scene).join('\n')}`, "color: #00ff00;");
    }).catch(error => console.error('Error fetching data:', error));
}

class ImageViewer extends LOD {
    constructor(renderer, url, x, y, w, h, tilex, tiley, offset, info, level) {
        super();
        console.log("ImageViewer( "+x+" "+y+" "+w+" "+h+"                "+tilex+" "+tiley+" "+offset+" "+level+" )");
        //console.log(info);
        //info['sizes'].forEach(
    //        element => {
    //            const nxtiles = Math.ceil(element.width);//tilex);
    //            const nytiles = Math.ceil(element.height);//tiley);
    //            console.log("scale : "+nxtiles+" "+nytiles);
    //        }
    //    );
        this.isImageViewer = true;
        this.type = 'ImageViewer';
        this.booted = false;
        this.level = level;
        this.edistance = 0.50 * w;
        const low = Square(renderer, x, y, w, h, srcurl(url, x, y, w, h, tilex, tiley), offset);
        low.name = "Square";
        low.frustumCulled = true;
        // console.log("addLevel : "+this.edistance);
        this.addLevel( low, this.edistance   );
        low.onBeforeRender = () => {
          //if (this.level < 1) {
            if (w >= tilex) {
            //if (w<0) {
                if (!this.booted) {          
                    this.booted = true;
                    const offx = Math.ceil( w / tilex ) / 2 * tilex;
                    const offy = Math.ceil( h / tiley ) / 2 * tiley;
                    const offm = Math.max(offx,offy);
                    const nextlevel = level + 1;
                    console.log("BOOM : "+x+"  "+y+"  "+w+"  "+h+"  "+offx+"  "+offy);
                    const nw = new ImageViewer(renderer, url, x,            y,                offm,     offm, tilex, tiley, offset, info, nextlevel );
                    const ne = new ImageViewer(renderer, url, x + offm - 0, y,            w - offm,     offm, tilex, tiley, offset, info, nextlevel );
                    const sw = new ImageViewer(renderer, url, x,            y + offm - 0,     offm, h - offm, tilex, tiley, offset, info, nextlevel );
                    const se = new ImageViewer(renderer, url, x + offm - 0, y + offm - 0, w - offm, h - offm, tilex, tiley, offset, info, nextlevel );
                    nw.position.set(     - offm   / 2,   offm / 2, 0);
                    ne.position.set( (  w - offm ) / 2,   offm / 2, 0);
                    sw.position.set(     - offm   / 2, - (  h - offm ) / 2, 0);
                    se.position.set( (  w - offm ) / 2, - (  h - offm ) / 2, 0);
                    const high = new Group();
                    high.add(nw);
                    high.add(ne);
                    high.add(sw);
                    high.add(se);
                    high.frustumCulled = true;
                    //   console.log("sub render : "+w+" "+h+" "+offx+" "+offy+" ===> "+(this.edistance/2));
                    if (w<=tilex) {
                        //     console.log("addSubLevel : ZERO");
                        this.addLevel( high, 0 );
                        this.bottom = true;
                    } else {
                        const sigh = 0.25 * this.edistance ;
                        //     console.log("addSubLevel : "+sigh);
                        this.addLevel( high, sigh );
                    }
                }
            } else {
                console.log("ROCK BOTTOM ON IMAGE....");
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

function CreateFeatureViewer(renderer, scene, url, offset) {
 //   console.log("Running CreateFeatureViewer");
 Cache.enabled = true;
  var target = url + "/info.json";
  fetch(target)
    .then(response => response.json())
    .then(data => {
      const x = 0;
      const y = 0;
      const w = data.width;
      const h = data.height;
      const offset = 0;
      const tilex = data.tiles[0].width;
      const tiley = data.tiles[0].height;
      const lod = new FeatureViewer(renderer, scene, url, x, y, w, h, tilex, tiley, offset, data, 0);
      lod.name = "FeatureViewer";
      lod.imageWidth = w;
      lod.imageHeight = h;
      lod.url = url;
      lod.frustrumCulled = false;      
      scene.add(lod);
     // lod.position.x = 100;
     // lod.position.y = 100;
      lod.position.z = 1;
    }).catch(error => console.error('Error fetching data:', error));
}

class FeatureViewer extends LOD {
  constructor(renderer, scene, url, x, y, w, h, tilex, tiley, offset, info, level) {
    super();
    console.log("FeatureViewer( "+x+" "+y+" "+w+" "+h+"                "+tilex+" "+tiley+" "+offset+" "+level+" )");
    this.isImageViewer = true;
    this.type = 'FeatureViewer';
    this.booted = false;
    this.level = level;
    this.edistance = 0.50 * w ;
    if (w<=tilex) {
        const loader = new FileLoader();
        const xsrc = url + "/" + x + "," + y + "," + w + "," + h + "/" + tilex + ",/0/default.json";
        loader.load(
            xsrc,            
            (data) => {
                const json = JSON.parse(data)["@graph"];
                const group = new Group();
                json.forEach(yyy=>{
                    group.add(createPolygon( yyy["hasGeometry"]["asWKT"], x, y ));
                });                
                this.addLevel( group, this.edistance   );
            },null,
            function (error) {
                console.error('An error happened', error);
            }
        );
    } else {    
        const low = Square(renderer, x, y, w, h, srcurl(url, x, y, w, h, tilex, tiley), offset);
        low.name = "Square";
        low.frustumCulled = true;
        console.log("addLevel : "+this.edistance);
        this.addLevel( low, this.edistance   );
        low.onBeforeRender = () => {
        //if (this.level < 1) {       
        //if (w<0) {
            if (!this.booted) {
              this.booted = true;
              const offx = Math.ceil( w / tilex ) / 2 * tilex;
              const offy = Math.ceil( h / tiley ) / 2 * tiley;
              const offm = Math.max(offx,offy);
              const nextlevel = level + 1;
              const nw = new FeatureViewer(renderer, scene, url, x,            y,                offm,     offm, tilex, tiley, offset, info, nextlevel );
              
              const ne = new FeatureViewer(renderer, scene, url, x + offm - 0, y,            w - offm,     offm, tilex, tiley, offset, info, nextlevel );
              const sw = new FeatureViewer(renderer, scene, url, x,            y + offm - 0,     offm, h - offm, tilex, tiley, offset, info, nextlevel );
              const se = new FeatureViewer(renderer, scene, url, x + offm - 0, y + offm - 0, w - offm, h - offm, tilex, tiley, offset, info, nextlevel );
            nw.position.set(      - offm   / 2,          offm   / 2, 0);
            ne.position.set( (  w - offm ) / 2,          offm   / 2, 0);
            sw.position.set(      - offm   / 2, - (  h - offm ) / 2, 0);
            se.position.set( (  w - offm ) / 2, - (  h - offm ) / 2, 0);
            console.log("OFFSETS : "+offx+"  "+offy);
              const high = new Group();
              high.add(nw);
              high.add(ne);
              high.add(sw);
              high.add(se);
              high.frustumCulled = true;
            //console.log("sub render : "+w+" "+h+" "+offx+" "+offy+" ===> "+(this.edistance/2));
            if (w<=tilex) {
              //    console.log("addSubLevel : ZERO");
                this.addLevel( high, 0 );
                this.bottom = true;
               } else {
                     const sigh = (0.25 * this.edistance);
                    console.log("addSubLevel : "+sigh);
                    this.addLevel( high, sigh );
            }
            }
        };
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

export {Square, CreateImageViewer, CreateFeatureViewer, createPolygon};
