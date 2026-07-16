package com.ebremer.halcyon.gui.tree;

import com.ebremer.ns.HAL;
import com.ebremer.vandegraph.GraphNode;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.shared.Lock;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.model.IModel;

/**
 * Checkbox model for the collection tree: reads/writes a node's
 * {@code hal:isSelected} flag and cascades the change — checking a node
 * selects its whole {@code so:hasPart} subtree, unchecking it deselects
 * the node's {@code so:isPartOf} ancestors.
 *
 * <p>Successor of {@code TreexNodePropertyModel}, now built on
 * {@link GraphNode#setFlag} instead of the prototype's
 * prefix-lookup binding.
 */
public class TreeSelectionModel implements IModel<Boolean> {
    private static final long serialVersionUID = 1L;

    private final IModel<GraphNode> node;

    public TreeSelectionModel(IModel<GraphNode> node) {
        this.node = node;
    }

    @Override
    public Boolean getObject() {
        return node.getObject().hasFlag(HAL.isSelected);
    }

    @Override
    public void setObject(Boolean value) {
        GraphNode n = node.getObject();
        boolean selected = Boolean.TRUE.equals(value);
        n.setFlag(HAL.isSelected, selected);
        ParameterizedSparqlString pss;
        if (selected) {
            pss = new ParameterizedSparqlString("""
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
        pss.setNsPrefix("", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("s", n.getURI());
        UpdateRequest request = UpdateFactory.create();
        request.add(pss.toString());
        Model m = n.getModel();
        m.enterCriticalSection(Lock.WRITE);
        try {
            UpdateAction.execute(request, m);
        } finally {
            m.leaveCriticalSection();
        }
    }

    @Override
    public void detach() {
        node.detach();
    }
}
