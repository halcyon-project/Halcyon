import {
	Object3D,
        LOD,
        Group,
        TextureLoader,
        Shape,
        ShapeGeometry,
        MeshBasicMaterial,
        DoubleSide,
        Mesh
} from 'three';

function srcurl(src,x,y,w,h,tilex,tiley) {
    const ha = src+"/"+x+","+y+","+w+","+h+"/"+tilex+",/0/default.png";
    console.log(ha);
    return new TextureLoader().load(ha);
}
        
function Square(x, y, w, h, src, offset) {
    var texture = src;
    const square = new Shape();
    square.moveTo(0, 0);
    square.lineTo(0, 1);
    square.lineTo(1, 1);
    square.lineTo(1, 0);
    const geometry = new ShapeGeometry(square);
    geometry.center();
    const material = new MeshBasicMaterial( { map: texture, depthWrite: false, side: DoubleSide } );
    const X = new Mesh(geometry, material);
    X.scale.x = w;
    X.scale.y = h;
    X.frustumCulled = false;
    X.position.set(0, 0, offset);
    return X;
}

function CreateImageViewer(scene, url, offset) {
    var target=url+"/info.json";
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
            const lod = new ImageViewer(url,x,y,w,h,tilex,tiley,offset,data);
            scene.add(lod);
        }).catch(error => console.error('Error fetching data:', error));
}

class ImageViewer extends LOD {
    constructor(url,x,y,w,h,tilex,tiley,offset,info) {
        super();
        this.isImageViewer = true;
        this.type = 'ImageViewer';
        this.booted = false;    
        const low = Square(x,y,w,h,srcurl(url,x,y,w,h,tilex,tiley),offset);   
        this.addLevel(low,w);    
        low.onBeforeRender = () => {
            if (w>1024) {
                if (!this.booted) {
                    this.booted = true;
                    const offx = Math.trunc(w/2);
                    const offy = Math.trunc(h/2);
                    const nw = new ImageViewer(url,x,y,offx,offy,tilex,tiley,offset,info);
                    const ne = new ImageViewer(url,x+offx,y,offx,offy,tilex,tiley,offset,info);
                    const sw = new ImageViewer(url,x,y+offy,offx,offy,tilex,tiley,offset,info);
                    const se = new ImageViewer(url,x+offx,y+offy,offx,offy,tilex,tiley,offset,info);
                    sw.position.set(-offx/2,-offy/2,0);
                    nw.position.set(-offx/2,offy/2,0);
                    se.position.set(offx/2,-offy/2,0);
                    ne.position.set(offx/2,offy/2,0);
                    const high = new Group();
                    high.add(nw);
                    high.add(ne);
                    high.add(sw);
                    high.add(se);
                    this.addLevel(high,w/2);
                }
            }
        };
    }
}
        
export { Square, CreateImageViewer };
