import * as THREE from '/threejs/build/three.module.js';

        function srcurl(x,y,w,h) {
            return new THREE.TextureLoader().load("/iiif/?iiif=http://localhost:8888/HalcyonStorage/tcga/coad/TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.svs/"+x+","+y+","+w+","+h+"/512,/0/default.jpg");
        }
        
        function srcsegurl(x,y,w,h) {
            return new THREE.TextureLoader().load("/halcyon/?iiif=file:///D:/HalcyonStorage/nuclearsegmentation2019/coad/TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip/"+x+","+y+","+w+","+h+"/512,/0/default.png");
        }
        
        async function featuresrcurl(x,y,w,h) {
            const response = await fetch("/halcyon/?iiif=file:///D:/HalcyonStorage/features/raj/Ptumor_heatmap_TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip/full/323,/0/default.json");
            const json = await response.json();
            return json;
        }
        
        function Square(x, y, w, h, src, offset) {
            var texture = src;
            const square = new THREE.Shape();
            square.moveTo(0, 0);
            square.lineTo(0, 1);
            square.lineTo(1, 1);
            square.lineTo(1, 0);
            const geometry = new THREE.ShapeGeometry(square);
            geometry.center();
            const material = new THREE.MeshBasicMaterial( { map: texture, depthWrite: false, side: THREE.DoubleSide } );
            const X = new THREE.Mesh(geometry, material);
            X.scale.x = w;
            X.scale.y = h;
            X.frustumCulled = false;
            X.position.set(0, 0, offset);
            return X;
        }

        function BlankSquare(x, y, w, h, offset) {
            const square = new THREE.Shape();
            square.moveTo(0, 0);
            square.lineTo(0, 1);
            square.lineTo(1, 1);
            square.lineTo(1, 0);
            const geometry = new THREE.ShapeGeometry(square);
            geometry.center();
            const material = new THREE.MeshBasicMaterial( { color: new THREE.Color(255,0,0).getHex(), depthWrite: false, side: THREE.DoubleSide } );
            const X = new THREE.Mesh(geometry, material);
            X.scale.x = w;
            X.scale.y = h;
            X.frustumCulled = false;
            X.position.set(0, 0, offset);
            return X;
        }

        function SegmentationSquare(x, y, w, h, src, offset) {
            var texture = src;
            const square = new THREE.Shape();
            square.moveTo(0, 0);
            square.lineTo(0, 1);
            square.lineTo(1, 1);
            square.lineTo(1, 0);
            const geometry = new THREE.ShapeGeometry(square);
            geometry.center();
            const material = new THREE.MeshBasicMaterial( { map: texture, depthWrite: false, side: THREE.DoubleSide, transparent: true, opacity: 0.5 } );
            const X = new THREE.Mesh(geometry, material);
            X.scale.x = w;
            X.scale.y = h;
            X.frustumCulled = false;
            X.position.set(0, 0, offset);
            return X;
        }
        
        function getJSON(x,y,w,h) {
            const loader = new THREE.FileLoader();
            var json = loader.load(
                // resource URL
                "/halcyon/?iiif=file:///D:/HalcyonStorage/nuclearsegmentation2019/TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip/"+x+","+y+","+w+","+h+"/512,/0/default.json",

        	// onLoad callback
                function ( data ) {
                  //  console.log(data);
                    var wow = JSON.parse(data);
                    console.log(wow);
                    return wow;
                },

                // onProgress callback
                function ( xhr ) {
                    console.log( (xhr.loaded / xhr.total * 100) + '% loaded' );
                },

                // onError callback
                function ( err ) {
                    console.error( 'An error happened' );
                }
            );
            console.log("OUT!");
            
            return json;
        }
    
        function BasicSquare(tile) {
            //console.log(tile);
            const poly = new THREE.Shape();
            const coords = tile.coordinates;
            const hasValue = tile.hasValue;
            poly.moveTo(0, 0);
            poly.lineTo(0, 1);
            poly.lineTo(1, 1);
            poly.lineTo(1, 0);
            //for (let i = 1; i < coords.length; i++) {
              //  poly.moveTo(coords[i].x/248, coords[i].y/248);
            //}
            const geometry = new THREE.ShapeGeometry(poly);
            geometry.center();
            const green = 255*(1-hasValue);
            const blue = 255*(1-hasValue);
            const red = 255*hasValue;
            const material = new THREE.MeshBasicMaterial({ color: new THREE.Color(red,green,0).getHex(), transparent: true, opacity: 0.5 });
            const X = new THREE.Mesh(geometry, material);
            X.scale.x = 347;
            X.scale.y = 347;
            X.frustumCulled = true;
            //console.log(coords[0][0]+"  "+coords[0][1]);
            const d = 347*239;
            X.position.set(347*coords[0][0]-(82984/2), d-(347*coords[0][1])-(82984/2), 1);
            return X;
        }

        
        class FeatureLayer2 extends THREE.Object3D {
            constructor(x,y,w,h,offset) {
		super();
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
		this.isFeatureLayer2 = true;
		this.type = 'FeatureLayer2';
                this.booted = false;
                const lod = new THREE.LOD();
                const low = SegmentationSquare(x,y,w,h,srcsegurl(x,y,w,h),offset);
                lod.addLevel(low,w);
                low.onBeforeRender = function() {
                    if (w>512) {
                        if (!this.booted) {
                            this.booted = true;
                            if (w>1024) {
                                const offx = Math.trunc(w/2);
                                const offy = Math.trunc(h/2);
                                const nw = new FeatureLayer2(x,y,offx,offy);
                                const ne = new FeatureLayer2(x+offx,y,offx,offy);
                                const sw = new FeatureLayer2(x,y+offy,offx,offy);
                                const se = new FeatureLayer2(x+offx,y+offy,offx,offy);
                                sw.position.set(-offx/2,-offy/2,offset);
                                nw.position.set(-offx/2,offy/2,offset);
                                se.position.set(offx/2,-offy/2,offset);
                                ne.position.set(offx/2,offy/2,offset);
                                const high = new THREE.Group();
                                high.add(nw);
                                high.add(ne);
                                high.add(sw);
                                high.add(se);
                                lod.addLevel(high,w/2);
                            } else {
                                //console.log("Render ---> "+x+","+y+","+w+","+h);
                                const offx = Math.trunc(w/2);
                                const offy = Math.trunc(h/2);
                                const pg = new THREE.Group();
                                const loader = new THREE.FileLoader();
                                loader.load(
                                    "/halcyon/?iiif=file:///D:/HalcyonStorage/nuclearsegmentation2019/TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip/"+x+","+y+","+w+","+h+"/512,/0/default.json",

                                    function ( data ) {
                                        var obj = JSON.parse(data);
                                        for (let i = 0; i < obj.length; i++) {
                                            var coord = obj[i].coordinates;
                                            const square = new THREE.Shape();
                                            var x0 = (coord[0][0]-x)-offx;
                                            var y0 = h-(coord[0][1]-y)-offy;
                                            square.moveTo(x0, y0);
                                            for (let j = 1; j < coord.length; j++) {
                                                var xn = (coord[j][0]-x)-offx;
                                                var yn = h-(coord[j][1]-y)-offy;
                                                square.lineTo(xn, yn);
                                            }
                                            const geometry = new THREE.ShapeGeometry(square);
                                            const material = new THREE.MeshBasicMaterial( { color:0xffff00, transparent: true, opacity: 0.5 } );
                                            const X = new THREE.Mesh(geometry, material);
                                            X.scale.x = 1;
                                            X.scale.y = 1;
                                            X.frustumCulled = false;
                                            pg.add(X);
                                            //console.log("added polygon to group");
                                        }
                                        //pg.position.set(x, y, offset+1);
                                        lod.addLevel(pg,w/2);
                                        //console.log("added polygon to LOD");
                                        //var yes = pg.clone();
                                        //yes.position.set(0, 0, offset+5);
                                        //console.log("added clone polygon to Scene");
                                    },

                                    function ( xhr ) {
                                        //console.log( (xhr.loaded / xhr.total * 100) + '% loaded' );
                                    },

                                    function ( err ) {
                                        console.error( 'An error happened' +err);
                                    }
                                );
                            }
                        }
                    }
                };
                return lod;
            }
        }
        
        class FeatureLayer extends THREE.Object3D {
            constructor(datum) {
		super();
		this.isFeatureLayer = true;
		this.type = 'FeatureLayer';
                const matrix = new THREE.Group();
                console.log("# of tiles = "+datum.length);
                for (let i = 0; i < datum.length; i++) {
                    //if (datum[i].hasValue>0.50) {
                        const tile = BasicSquare(datum[i]);
                        matrix.add(tile);
//                    }
                } 
                return matrix;
            }
        }
        async function addlayer(x,y,w,h) {
            //console.log("Getting Feature Data");
            //const datum = await featuresrcurl(x,y,w,h);
            console.log("Creating Feature Layer");
            //const feature = new FeatureLayer(datum);
            //scene.add( feature );
            const feature2 = new FeatureLayer2(datum);
            scene.add( feature2 );
            console.log("Segmentation Layer Added");
        }
        
function getJsonFromUrl(url) {
    return fetch(url)
        .then(response => {
            if (!response.ok) {
                throw new Error("HTTP error " + response.status);
            }
            return response.json();
        })
        .catch(function() {
            console.log("Fetch error");
        });
}

class ImageViewer extends THREE.Object3D {
    constructor(x,y,w,h,url,offset,params,level) {
        if (level!==0) {
            console.log("ImageViewer "+x+" "+y+" "+w+" "+h+" "+level);
        }
        super();
	this.isZephyr = true;
	this.type = 'ImageViewer';
        this.booted = false;
        const scale = params.info.tiles[0].scaleFactors[level];
        var tw = params.tw*scale;
        var th = params.th*scale;
        var nx = Math.ceil(w/tw);
        var ny = Math.ceil(h/th);
        const lod = new THREE.LOD();
        const main = new THREE.Group();
        //nx = 1;
        //ny = 1;
        for (let j=0; j<ny; j++) {
            for (let i=0; i<nx; i++) {
                var a = x + (i * tw);
                var b = y + (j * th);
                var aw = tw - (x % tw);
                var ah = th - (y % th);
                var low = this.Square(aw,ah,this.xsrcurl(url,a,b,aw,ah,params.tw,params.th),offset);
                low.position.set(a,Math.round(params.height)-b,0);
                low.onBeforeRender = function() {
                    //console.log("onBeforeRender "+level+" "+p.numlevels);
                    if (level<params.numlevels) {
                        if (!this.booted) {
                            this.booted = true;
                            
                            
                            
                            
                            
                            /*
                            const offx = Math.trunc(Math.round(aw/2));
                            const offy = Math.trunc(Math.round(ah/2));
                            const nw = new ImageViewer(a,b,offx,offy,url,offset,params,level+1);
                            const ne = new ImageViewer(a+offx,b,offx,offy,url,offset,params,level+1);
                            const sw = new ImageViewer(a,b+offy,offx,offy,url,offset,params,level+1);
                            const se = new ImageViewer(a+offx,b+offy,offx,offy,url,offset,params,level+1);
                            sw.position.set(-offx/2,-offy/2,0);
                            nw.position.set(-offx/2,offy/2,0);
                            se.position.set(offx/2,-offy/2,0);
                            ne.position.set(offx/2,offy/2,0);
                            const sub = new THREE.Group();
                            sub.add(nw);
                            sub.add(ne);
                            sub.add(sw);
                            sub.add(se);
                            lod.addLevel(sub,w/4);*/
                        }
                    }
                };
                main.add(low);
            }
        }
        lod.addLevel(main,w);
        return lod;
    }
    
    static async createAsync(url,offset,params) {
        if (params===null) {
            params = {};
        }
        const loader = new THREE.FileLoader();
        var asyncinfo = loader.loadAsync('/iiif/?iiif='+url+'/info.json',null);
        var infojson = await asyncinfo;
        var info = JSON.parse(infojson);
        params.info = info;
        params.numlevels = info.sizes.length;
        params.width = info.width;
        params.height = info.height;
        params.tw = info.tiles[0].width;
        params.th = info.tiles[0].height;
        const grids = [];
        for (let a=0; a<info.sizes.length; a++) {
            const grid = {};
            grid.nx = Math.ceil(info.sizes[a].width/params.tw);
            grid.ny = Math.ceil(info.sizes[a].height/params.th);
            grids[a] = grid;
        }
        params.grids = grids;
        console.log(params);
        return new ImageViewer(0,0,info.width,info.height,url,offset,params,0);
    }
    
    xsrcurl(url,x,y,w,h,tx,ty) {
        return new THREE.TextureLoader()
                .load("/iiif/?iiif="+url+"/"+x+","+y+","+w+","+h+"/"+tx+",/0/default.jpg",
                function(texture) {
                    texture.wrapS = THREE.ClampToEdgeWrapping;
                    texture.wrapT = THREE.ClampToEdgeWrapping;
                    //texture.repeat.set(1, 1);
                });
    }
    
    Square(w, h, texture, offset) {
        console.log("Square : "+w+" "+h+" "+texture+" "+offset);
        const square = new THREE.Shape();
        square.moveTo(0, 0);
        square.lineTo(0, 1);
        square.lineTo(1, 1);
        square.lineTo(1, 0);
        const geometry = new THREE.ShapeGeometry(square);
        geometry.center();
        const material = new THREE.MeshBasicMaterial( { map: texture, depthWrite: false, side: THREE.DoubleSide } );
        const X = new THREE.Mesh(geometry, material);
        X.scale.x = w;
        X.scale.y = h;
        X.frustumCulled = false;
        X.position.set(0, 0, offset);
        material.needsUpdate = true;
        return X;
    }
}

function getIIIFInfo(url) {
    getJsonFromUrl(url)
        .then(json => {
            console.log(json);
            return json;
        });   
}
        
export {ImageViewer, FeatureLayer2};
