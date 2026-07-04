import {
    Scene
} from 'three';

import {
    Stack
} from 'zephyr';

function ParseTTL(turtleData, store, baseURI) {
    try {
        $rdf.parse(turtleData, store, baseURI, 'text/turtle');
    } catch (err) {
        console.error('Error parsing Turtle:', err);       
    }
}

function DumpTTL(store, baseURI) {
    let serializedGraph = '';
    serializedGraph = $rdf.serialize(null, store, baseURI, 'text/turtle');
    console.log("--- Serialized Turtle Output ---");
    console.log(serializedGraph);
}

function ListElements(store, baseURI) {
  const RDF  = $rdf.Namespace('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
  const zeph = $rdf.Namespace('https://halcyon.is/zephyr/ns/');
  const ZEPH_NS = zeph('').value;
  return store
    .match(null, RDF('type'), null)
    .filter(quad =>
      quad.object.termType === 'NamedNode' &&
      quad.object.value.startsWith(ZEPH_NS)
    );
}

function ListImages(store, baseURI) {
    const zeph = $rdf.Namespace('https://halcyon.is/zephyr/ns/');
    const stacks = store.match(null, $rdf.sym('http://www.w3.org/1999/02/22-rdf-syntax-ns#type'), zeph('Stack'));
    stacks.forEach(stack => {
        const layers = store.match(stack.subject, zeph('layers'), null);
        const layerList = layers[0].object.elements;
        layerList.forEach(layerName => {
            const image = store.match(layerName, zeph('src'), null);
            console.log("Image : "+ image[0].object.value);     
        });
    });
}

class WE {
    zeph = $rdf.Namespace('https://halcyon.is/zephyr/ns/');
    
    constructor(scene, store) {
        this.scene = scene;
        this.store = store;
    }
    
    getStore() {
        return this.store;
    }
    
    getScene() {
        return this.scene;
    }
    
    add( statement ) {
        switch(statement.object.value) {
            case this.zeph('Stack').value: {
                // Build only ROOT stacks here; a nested zeph:Stack is the object
                // of some zeph:src and is built recursively by its parent, so
                // skip it to avoid constructing the subtree twice.
                const subject = statement.subject;
                const nested = this.store.match(null, this.zeph('src'), subject).length > 0;
                if (nested) {
                    return;
                }
                console.log("Zephyr: building root stack " + subject.value);
                this.scene.add(new Stack(this, statement));
                break;
            }
            default:
                // Other typed nodes (FeatureLayer/ImageLayer/etc.) are handled
                // as members while building their parent stack.
        }
    }
}

export { ParseTTL, DumpTTL, ListElements, ListImages, WE };
