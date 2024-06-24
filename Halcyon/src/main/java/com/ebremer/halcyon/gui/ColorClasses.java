package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.puffin.CommandNode;
import com.ebremer.halcyon.puffin.SHACLForm;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.ns.HAL;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author erich
 */
public class ColorClasses extends BasePage {
    private static final long serialVersionUID = 1L;
    
    public ColorClasses() {
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createProperty(HAL.NS+HalcyonSession.get().getUserURI()+"/colorclasses");
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        if (ds.containsNamedModel(r)) {
            m.add(ds.getNamedModel(r));
        }
        ds.end();
        Resource key;
        if (m.contains(r, RDF.type, HAL.AnnotationClassList)) {
            key = m.listSubjectsWithProperty(RDF.type, HAL.AnnotationClassList).next();
        } else {
            m.add(r, RDF.type, HAL.AnnotationClassList);
            key = r;
        }
        CommandNode cn = new CommandNode(r,m);
        SHACLForm sf = new SHACLForm("sform", key, HAL.AnnotationClassListShape.asNode(), cn);
        add(sf);
    }
}
