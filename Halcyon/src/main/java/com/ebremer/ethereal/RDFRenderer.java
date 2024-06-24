package com.ebremer.ethereal;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PropertyNotFoundException;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class RDFRenderer implements IChoiceRenderer {
    private final RDFDetachableModel rdg;
    
    public RDFRenderer(RDFDetachableModel rdg) {
        this.rdg = rdg;
    }

    @Override
    public Object getDisplayValue(Object t) {
        Model m = rdg.getObject();
        Node r = (Node) t;
        Statement s;
        try {           
            s = m.getRequiredProperty(m.createResource(r.toString()), DCTerms.title);
        } catch (PropertyNotFoundException ex) {
            try {           
                s = m.getRequiredProperty(m.createResource(r.toString()), SchemaDO.name);
            } catch (PropertyNotFoundException ex2) {
                return switch (r.toString()) {
                    case "urn:halcyon:nocollections" -> "not specified";
                    case "urn:halcyon:allcollections" -> "All";
                    default -> r.toString();
                };
            }
        }
        return s.getObject().asLiteral().getString();
    }

    @Override
    public String getIdValue(Object object, int index) {
        return IChoiceRenderer.super.getIdValue(object, index);
    }

    @Override
    public Object getObject(String id, IModel choices) {
        Object o = IChoiceRenderer.super.getObject(id, choices);
        return o;
    }

    @Override
    public void detach() {
        IChoiceRenderer.super.detach();
    }
}
