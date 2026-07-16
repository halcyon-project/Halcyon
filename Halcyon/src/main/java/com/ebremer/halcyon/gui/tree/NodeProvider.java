package com.ebremer.halcyon.gui.tree;

import com.ebremer.vandegraph.SessionScopedModel;
import com.ebremer.vandegraph.GraphNode;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.ns.HAL;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class NodeProvider implements ITreeProvider<GraphNode> {
    private static final long serialVersionUID = 1L;
    private final SessionScopedModel rdm;
    private String collection;
    
    public NodeProvider() {
        DataCore dc = DataCore.getInstance();
        Dataset ds = dc.getDataset();
        Model h = ModelFactory.createDefaultModel();
        // H13: end() in a finally.
        ds.begin(ReadWrite.READ);
        try {
            h.add(ds.getNamedModel(HAL.CollectionsAndResources));
        } finally {
            ds.end();
        }
        rdm = new SessionScopedModel(h);
    }
    
    public Model getRDFModel() {
        return rdm.getObject();
    }
    
    public String getCollection() {
        return collection;
    }
    
    public void SetSelected(String collection) {
        this.collection = collection;
        UpdateRequest update = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
        """
            delete {?s :isSelected ?o}
            where {
                ?s :isSelected ?o
            }
        """);
        pss.setNsPrefix("", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("collection", collection);
        update.add(pss.toString());
        pss.setCommandText("""
            insert {?s :isSelected false}
            where {
                ?s a so:Dataset .
                minus {?s so:isPartOf ?collection}
            }
        """);
        update.add(pss.toString());
        pss.setCommandText("""
            insert {?o :isSelected false}
            where {
                ?s a so:Dataset; so:hasPart ?o
                minus {?o so:isPartOf ?collection}
            }
        """);
        update.add(pss.toString());
        pss.setCommandText("""
            insert {?s :isSelected true}
            where {?s so:isPartOf ?collection}
        """);
        update.add(pss.toString());
        UpdateAction.execute(update, getRDFModel());
    }
    
    public void DeselectAll(String collection) {
        this.collection = collection;
        UpdateRequest update = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            delete {?s :isSelected ?o}
            where {
                ?s :isSelected ?o
            }
        """);
        pss.setNsPrefix("", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("collection", collection);
        update.add(pss.toString());
        pss.setCommandText(
            """
            delete {?o so:isPartOf ?collection}
            where {
                ?o so:isPartOf ?collection
            }
            """
        );
        pss.setIri("collection", collection);
        update.add(pss.toString());
        pss.setCommandText(
            """
            delete {?s so:isPartOf ?collection}
            where {
                ?s so:isPartOf ?collection
            }
            """
        );
        pss.setIri("collection", collection);
        update.add(pss.toString());
        UpdateAction.execute(update, getRDFModel());
    }
    
    @Override
    public Iterator<GraphNode> getRoots() {
        ArrayList<GraphNode> ar = new ArrayList<>();
        Model m = rdm.getObject();
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?s where {
                ?s a so:Dataset; so:hasPart ?o
                minus {?ha so:hasPart ?s}
            }
        """);
        pss.setNsPrefix("so", SchemaDO.NS);
        // H13: in-memory model (no transaction to strand), but close the execution.
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            qe.execSelect().forEachRemaining(qs->{
                Resource r = qs.getResource("s").asResource();
                ar.add(new GraphNode(r.asNode(),rdm));
            });
        }
        return ar.iterator();
    }

    @Override
    public boolean hasChildren(GraphNode node) {
        Model m = rdm.getObject();
        Resource r = m.createResource(node.getNode().toString());
        boolean hasChildren = m.getProperty(r, SchemaDO.hasPart) != null;
        return hasChildren;
    }

    @Override
    public Iterator<GraphNode> getChildren(GraphNode t) {
        Model m = rdm.getObject();
        ArrayList<GraphNode> ar = new ArrayList<>();
        NodeIterator ni = m.listObjectsOfProperty(m.asRDFNode(t.getNode()).asResource(), SchemaDO.hasPart);
        while (ni.hasNext()) {
            ar.add(new GraphNode(ni.nextNode().asNode(),rdm));
        }
        return ar.iterator();
    }

    @Override
    public IModel<GraphNode> model(GraphNode foo) {
        return new FooModel(foo);
    }
    
    @Override
    public void detach() {}
    
    private static class FooModel extends LoadableDetachableModel<GraphNode> {
        private static final long serialVersionUID = 1L;
        private final String id;
        private final GraphNode local;

        public FooModel(GraphNode foo) {
            super(foo);
            id = foo.getURI();
            local = foo;
        }

        @Override
        protected GraphNode load() {
            return local;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FooModel fooModel) {
                return fooModel.id.equals(id);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
