package com.ebremer.halcyon.gui.tree;

import com.ebremer.ethereal.xNode;
import com.ebremer.ethereal.xNodePropertyModel;
import com.ebremer.ns.HAL;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 * @param <T>
 */
public class TreexNodePropertyModel<T> extends xNodePropertyModel<T> {
    
    public TreexNodePropertyModel(Object modelObject, String expression) {
        super(modelObject, expression);
    }
    
    @Override
    public void setObject(T object) {
        boolean x = (boolean) object;
        super.setObject(object);
        final Object target = getInnermostModelOrObject();
        if (target instanceof xNode xn) {
            Model m = xn.getModel();
            Resource r = m.createResource(getNode().getNode().toString());
            if (propertyExpression().equals("isSelected")) {
                UpdateRequest request = UpdateFactory.create();
                request.setPrefix("", HAL.NS);
                request.setPrefix("so", SchemaDO.NS);
                ParameterizedSparqlString pss;
                if (x) {
                    pss = new ParameterizedSparqlString(
                    """
                    delete {?o :isSelected ?isSelected}
                    insert {?o :isSelected true}
                    where {?s so:hasPart+ ?o . ?o :isSelected ?isSelected}
                    """);
                } else {
                    pss = new ParameterizedSparqlString("""
                        delete {?o :isSelected ?isSelected}
                        insert {?o :isSelected false}
                        where {?s so:isPartOf+ ?o . ?o :isSelected ?isSelected}
                        """);
                }
                pss.setIri("s", r.getURI());
                request.add(pss.toString());
                UpdateAction.execute(request, m);
            } else {
                System.out.println("SERIOUS SYSTEM ERROR");
            }
        } else {
            throw new UnsupportedOperationException("AbstractxNodeModel : setObject: Can't handle this object type.  Sorry sibling.");
        }
    }
}
