package com.ebremer.halcyon.converters.hold;

import com.ebremer.ns.HAL;
import com.ebremer.ns.SNO;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 *
 * @author erich
 */
public class Features {
    private final Model Features = ModelFactory.createDefaultModel();
    private final HashMap<String,Integer> f = new HashMap<>();
    private final HashMap<String,String> names = new HashMap<>();
    
    public Features() {
        names.put(SNO.Unknown.getURI(),"Unknown");
        names.put(SNO.Lymphocytes.getURI(),"Lymphocyte");
        names.put(SNO.TumorCell.getURI(),"Tumor Cell");
        names.put(SNO.Cell.getURI(),"Cell");
        names.put(SNO.Misc.getURI(),"Miscellaneous");
        names.put(SNO.Tumor.getURI(),"Tumor");
        names.put(SNO.Stroma.getURI(),"Stroma");
        names.put(SNO.Epithelium.getURI(),"Epithelium");
        names.put(SNO.Dysplasia.getURI(),"Dysplasia");
        names.put(SNO.Necrosis.getURI(),"Necrosis");
    }
    
    public Resource Name2IRI(String name) {
        Resource ciri;
        if (name.startsWith("https://")||name.startsWith("http://")||name.startsWith("urn:")) {
            ciri = Features.createResource(name);
        } else {
            ciri = Features.createResource("urn:featureclassid:"+name);
        }
        return ciri;
    }
    
    public Model getModel(Resource r) {
        Iterator<String> i = f.keySet().iterator();
        Model m = ModelFactory.createDefaultModel();
        while (i.hasNext()) {
            String key = i.next();
            System.out.println("GETTING FEATURE --> "+key);
            Resource fea = m.createResource(key);
            Resource bn = m.createResource();
            m.add(bn,RDF.type,fea);
            if (names.containsKey(key)) {
                m.add(fea,RDFS.label,names.get(key));
            }
            int k = f.get(key);
            m.addLiteral(bn,RDF.value, k);
            if (names.containsKey(key)) {
                m.add(bn,RDFS.label,names.get(key));
            } else {
                if (key.startsWith("urn:")) {
                    String[] crack = key.split(":");
                    m.add(bn,RDFS.label,crack[crack.length-2]+":"+crack[crack.length-1]);
                } else {
                    m.add(bn,RDFS.label,"Unknown");
                }
            }
            m.add(r,HAL.hasFeature,bn);
        }
        return m;
    }
    
    public void AddClass(String name) {
        Resource ciri = Name2IRI(name);
        String nam = ciri.getURI();
        if (!Features.contains(ciri, RDFS.label, name)) {
            Features.add(ciri, RDFS.label, name);
            if (!f.containsKey(nam)) {
                System.out.println("adding Feature : "+nam);
                f.put(nam, f.size()+1);
            } else {
                System.out.println("exists!Feature : "+nam);
            }
        }
    }
    
    public int getClassID(String name) {
        return f.get(Name2IRI(name).getURI());
    }
}
