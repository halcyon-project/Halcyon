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
  Box3
} from 'three';

// import {dumpObject} from './dumpObject.js';

function srcurl(src, x, y, w, h, tilex, tiley) {
  const ha = src + "/" + x + "," + y + "," + w + "," + h + "/!" + tilex + ","+ tiley+"/0/default.png";
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
  //geometry.translate(0,-1,0);
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
      // console.log("data:", data);
      const lod = new ImageViewer(renderer, url, x, y, w, h, tilex, tiley, offset, data, 0, "ROOT");
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
    constructor(renderer, url, x, y, w, h, tilex, tiley, offset, info, level, name) {
        super();
        console.log("ImageViewer( "+name +" "+x+" "+y+" "+w+" "+h+"     "+tilex+" "+tiley+" "+offset+" "+level+" )");
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
        this.edistance = 0.95 * w;
        const low = Square(renderer, x, y, w, h, srcurl(url, x, y, w, h, tilex, tiley), offset);
        low.name = "Square";
        low.frustumCulled = true;
        //low.position.set(x+(w/2),y+(h/2));
        if (level===0) {
            const offx = ( Math.ceil( w / ( 2 * tilex ) ) / 2 ) * 2 * tilex;
            const offy = ( Math.ceil( h / ( 2 * tiley ) ) / 2 ) * 2 * tiley;
            const offm = Math.max(offx,offy);
            low.position.set((w/2)-offm,-(h/2)+offm);
        } else {
            //low.position.set(0,0);
        }
        if (level===200) {
            low.position.set(x, -y);
            console.log("=========> LEVEL : "+level);
        }
        this.addLevel( low, this.edistance   );
        low.onBeforeRender = () => {
          if (this.level < 5) {
            //if (w >= tilex) {
            //if (w<0) {
                if (!this.booted) {          
                    this.booted = true;
                    const offx = ( Math.ceil( w / ( 2 * tilex ) ) / 2 ) * 2 * tilex;
                    const offy = ( Math.ceil( h / ( 2 * tiley ) ) / 2 ) * 2 * tiley;
                    const offm = Math.max(offx,offy);                           
                    const nextlevel = level + 1;
                    const high = new Group();
                    const nw_width = (w>offm)?offm:w;
                    const nw_height = (h>offm)?offm:h;
                    const ne_width = w-offm;
                    const ne_height = nw_height;
                    const sw_width = nw_width;
                    const sw_height = h-offm;
                    const nw = new ImageViewer(renderer, url, x, y, nw_width, nw_height, tilex, tiley, offset, info, nextlevel, name+"/NW" );
                    switch (level) {
                        case 0:
                            nw.position.set( -nw_width/2, nw_height/2, 0);
                            high.add(nw);
                            break;
                        case 1:
                            if (name==="ROOT/SW") {
                               nw.position.set( -nw_width/2, (nw_height/2)-(level*nw_height/2), 0);
                                high.add(nw);
                            }
                            break;
                    }
                    if (w>offm) {
                        const ne = new ImageViewer(renderer, url, x + nw_width, y, ne_width, ne_height, tilex, tiley, offset, info, nextlevel, name+"/NE" );
                        switch (level) {
                            case 0:
                                ne.position.set( ne_width/2, ne_height/2, 0);
                                high.add(ne); 
                                break;
                            case 1:
                                if (name === "ROOT/NE") {
                                    ne.position.set( nw_width/2, nw_height/2, 0);
                                    high.add(ne); 
                                }
                                break;
                        }
                    }
                    if (h>offm) {
                        const sw = new ImageViewer(renderer, url, x, y + nw_height, sw_width, sw_height, tilex, tiley, offset, info, nextlevel, name+"/SW" );
                        switch (level) {
                            case 0:
                               // if (name==="ROOT") {
                                    sw.position.set( -nw_width/2, -sw_height/2, 0);
                                    high.add(sw); 
                               // }
                                break;
                            case 1:
                                sw.position.set( -nw_width/2, -sw_height/2, 0);
                                high.add(sw); 
                                break;
                            case 2:
                                sw.position.set( -nw_width/2, -sw_height/2, 0);
                                high.add(sw); 
                                break;
                            case 3:
                                sw.position.set( -nw_width/2, -sw_height/2, 0);
                                high.add(sw); 
                                break;
                            case 4:
                                sw.position.set( -nw_width/2, -sw_height/2, 0);
                                high.add(sw); 
                                break;                                
                        }                        
                    } else {
                        console.log("DIED : "+h+"  "+offm);
                    }
                    console.log("HERE ===> "+name+" "+level+" "+w+" "+offm+" "+h);                    
                    if ((w>offm)&&(h>offm)) {
                        const se = new ImageViewer(renderer, url, x + nw_width, y + nw_height , ne_width, sw_height, tilex, tiley, offset, info, nextlevel, name+"/SE" );
                        switch (level) {
                            case 2:
                                if (name==="ROOT/SW/NW") {
                                    console.log("FIRE ONE");
                                    se.position.set( nw_width/2, -sw_height/2, 0);
                                    high.add(se);
                                }
                                break;
                            case 3:
                                if (name==="ROOT/SW/NW/SE") {
                                    console.log("FIRE ONE");
                                    se.position.set( nw_width/2, -sw_height/2, 0);
                                    high.add(se);
                                }
                                break;
                        }
                    }
                    high.frustumCulled = true;
                  //  high.position.set(x+nw_width/4, y+nw_height/4);
                  //  high.position.set(x-(Math.pow(2,level-1)*nw_width),y);
                    if (w<=tilex) {
                        this.addLevel( high, 0 );
                        this.bottom = true;
                        console.log("ADD LEVEL : ZERO");
                    } else {
                        const sigh = 0.85 * this.edistance ;
                        this.addLevel( high, sigh );
                        console.log("ADD LEVEL : "+sigh);
                    }
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

export {Square, CreateImageViewer, CreateFeatureViewer, createPolygon, DrawAxis};
