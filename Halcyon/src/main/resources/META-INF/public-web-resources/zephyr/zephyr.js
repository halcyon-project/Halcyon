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
  LinearFilter,
  Box3,
  Texture,
  ShaderMaterial,
  Color,
  RepeatWrapping,
  ClampToEdgeWrapping,
  NearestFilter
} from 'three';

// import {dumpObject} from './dumpObject.js';

function srcurl(src, x, y, w, h, tilex, tiley, scale, name) {
   // if (name.startsWith("ROOT/SE/")) console.log("      Image( "+name+" "+x+" "+y+" "+w+" "+h+" "+tilex+" "+tiley+" "+scale+" )");
    const a = Math.trunc(w);
    const b = Math.trunc(h);
    const m = Math.trunc(Math.round(w*scale));
    const n = Math.trunc(Math.round(h*scale));    
    if ((w<1)||(h<1)) {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = 256;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = 'green';
        ctx.fillRect(0, 0, 256, 256);
        ctx.clearRect(0, 0, 256, 256);
        const newTexture = new Texture(canvas);
        newTexture.needsUpdate = true;
        return newTexture;
    } else {    
    const ha = `${src}/${x},${y},${a},${b}/!${m},${n}/0/default.png`;
    return new TextureLoader().load(ha,
        (texture) => {
           // if ( texture.image.width !== texture.image.height ) {
                texture.wrapS = ClampToEdgeWrapping;
                texture.wrapT = ClampToEdgeWrapping;
                texture.minFilter = NearestFilter;
                texture.magFilter = NearestFilter;
                texture.colorSpace = SRGBColorSpace;
                texture.generateMipmaps = false;
                texture.needsUpdate = true;
                const wratio = tilex / texture.image.width;
                const hratio = tiley / texture.image.height;
              //  console.log("RATIO : "+wratio+" "+hratio+"      "+texture.image.width+" "+texture.image.height+" "+tilex+" "+tiley+"  "+scale);
                texture.repeat.set( wratio, hratio );
                texture.offset.set( 0, 1 - hratio );
            //}
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
  //texture.colorSpace = SRGBColorSpace;
  const square = new Shape();
  square.moveTo(0, 0);
  square.lineTo(0, 1);
  square.lineTo(1, 1);
  square.lineTo(1, 0);
  const geometry = new ShapeGeometry(square);
  geometry.center();
  const textureMaterial = new MeshBasicMaterial({map: texture, depthWrite: false, side: DoubleSide, transparent: true});
  
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
  //texture.colorSpace = SRGBColorSpace;
  const X = new Mesh(geometry, textureMaterial);
  //X.scale.x = 1;
  //X.scale.y = 1;
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

function DrawAxis(scene) {
    const material = new LineBasicMaterial( { color: 0x0000ff } );
    const points = [];
    points.push( new Vector3( -200000, 0, 5 ) );
    points.push( new Vector3( 200000, 0, 5 ) );
    const geometry = new BufferGeometry().setFromPoints( points );
    const line = new Line( geometry, material );
    scene.add(line);
    const points2 = [];
    points2.push( new Vector3( 0, -200000, 5 ) );
    points2.push( new Vector3( 0, 200000, 5 ) );
    const geometry2 = new BufferGeometry().setFromPoints( points2 );
    const line2 = new Line( geometry2, material );
    scene.add(line2);
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
      const lod = new ImageViewer(renderer, url, w, h, x, y, w, h, tilex, tiley, offset, data, 0, "ROOT", 0, 0);
      lod.name = "ImageViewer";
      lod.imageWidth = w;
      lod.imageHeight = h;
      lod.url = url;
      lod.frustrumCulled = false;
      lod.scale.x = w;
      lod.scale.y = w;
      scene.add(lod);
    }).catch(error => console.error('Error fetching data:', error));
}

class ImageViewer extends LOD {
    constructor(renderer, url, width, height, x, y, w, h, tilex, tiley, offset, info, level, name, a, b) {
        super();
        //if (name.startsWith("ROOT/SE/")) console.log("ImageViewer( "+name +" "+width+" "+height+" "+tilex+" "+tiley+" "+offset+" "+level+" "+a+" "+b+")");       
        this.isImageViewer = true;
        this.type = 'ImageViewer';
        this.booted = false;
        this.level = level;        
        this.a = a;
        this.b = b;
        const numtilesx = Math.pow(2,level);
        const numtilesy = Math.pow(2,level);
        const tw = 2 * tilex * Math.ceil( width /  ( 2 * tilex )) / numtilesx;
        const th = 2 * tiley * Math.ceil( height /  ( 2 * tiley )) / numtilesy ;
        const ts = Math.round(Math.max(tw,th));
        this.shrink = tilex/ts;
        const ttw = (( a*ts+ts ) > width  ) ? ts - ( ( a*ts+ts ) - width  ) : ts;
        const tth = (( b*ts+ts ) > height ) ? ts - ( ( b*ts+ts ) - height ) : ts;
        const low = Square(renderer, srcurl(url, a*ts, b*ts, ttw, tth, tilex, tiley, this.shrink, name), offset, name);
        low.name = "Square";
        low.frustumCulled = true;
        this.edistance = width / Math.pow(2,level);
        this.addLevel( low,  this.edistance  );
        low.onBeforeRender = () => {
          //if ((this.level < 3)&&(!this.booted)) {
          //if (ts >= tilex) {
                if (!this.booted) {
                    this.booted = true;                       
                    const nextlevel = level + 1;
                    const high = new Group();
                    const nw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name+"/NW", 2*this.a , 2*this.b );
                    nw.position.set( -0.25, 0.25, 0);
                    nw.scale.x = 0.5;
                    nw.scale.y = 0.5;
                    high.add(nw);
                    if (ttw/2>1) {
                        const ne = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name+"/NE", 2*this.a + 1, 2*this.b );
                        ne.position.set( 0.25, 0.25, 0);
                        ne.scale.x = 0.5;
                        ne.scale.y = 0.5;
                        high.add(ne); 
                    }                    
                    if (tth/2>1) {
                        const sw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name+"/SW", 2*this.a , 2*this.b + 1);
                        sw.position.set( -0.25, -0.25, 0);
                        sw.scale.x = 0.5;
                        sw.scale.y = 0.5;
                        high.add(sw);
                    }                    
                    if ((ttw/2>1)&&(tth/2>1)) {
                        const se = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name+"/SE", 2*this.a + 1, 2*this.b + 1 );
                        se.position.set( 0.25, -0.25, 0);
                        se.scale.x = 0.5;
                        se.scale.y = 0.5;
                        high.add(se);                     
                    }
                    high.frustumCulled = true;
                    if (ts<=2*tilex) {
                        this.addLevel( high, 0 );
                        this.bottom = true;
                        //console.log("ADD LEVEL : ZERO");
                    } else {
                        const sigh = 0.25 * this.edistance;
                        this.addLevel( high, sigh );
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

function CreateFeatureViewer(renderer, scene, url, offset) {
 //   console.log("Running CreateFeatureViewer");
 
 /*
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
    }).catch(error => console.error('Error fetching data:', error));*/
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
    if (this.level < 1) {
    //if (w<=tilex) {
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
              const high = new Group();
              const nw = new FeatureViewer(renderer, scene, url, x,            y,                Math.min(offm,w),     Math.min(offm,h), tilex, tiley, offset, info, nextlevel );              
              nw.position.set(      - Math.min(offm,w)   / 2,          Math.min(offm,h)   / 2, 0);
              high.add(nw);
              console.log("FNW : "+ x+" "      +       y+" "+         offm +" "+   offm);
              const ww = x+offm;
              const hh = y+offm;
              if (ww<=w) {
                const ne = new FeatureViewer(renderer, scene, url, x + offm, y,            w - ww,     Math.min(offm,h), tilex, tiley, offset, info, nextlevel );
                ne.position.set( (  w - ww ) / 2,          Math.min(offm,h)   / 2, 0);
                high.add(ne);
                console.log("FNE : "+(x+offm)+" "+  y+" "+(w -    offm)+" "+   offm);
              }
              if (hh<=h) {
                const sw = new FeatureViewer(renderer, scene, url, x,            y + offm,     Math.min(offm,w), h - hh, tilex, tiley, offset, info, nextlevel );
                sw.position.set(      - Math.min(offm,w)   / 2, - (  h - hh ) / 2, 0);
                high.add(sw);  
                console.log("FSW : "+ x+" "      + (y+offm)+" "+   offm +" "+(h-offm));
              }
              if ((ww<=w)&&(hh<=h)) {
                const se = new FeatureViewer(renderer, scene, url, x + offm, y + offm, w - ww, h - hh, tilex, tiley, offset, info, nextlevel );
                se.position.set( (  w - ww ) / 2, - (  h - hh ) / 2, 0);
                high.add(se);
                console.log("FSE : "+(x+offm)+" "+ (y+offm)+" "+(w-offm)+" "+(h-offm));
            }
              high.frustumCulled = true;
            //console.log("sub render : "+w+" "+h+" "+offx+" "+offy+" ===> "+(this.edistance/2));
            if (w<=tilex) {
              //    console.log("addSubLevel : ZERO");
                this.addLevel( high, 0 );
                this.bottom = true;
               } else {
                     const sigh = (0.25 * this.edistance);
                    //console.log("addSubLevel : "+sigh);
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

export {Square, CreateImageViewer, CreateFeatureViewer, createPolygon, DrawAxis};
