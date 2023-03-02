/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.gui.tree;

import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ethereal.xNode;
import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.ns.HAL;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
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
public class NodeProvider implements ITreeProvider<xNode> {
    private static final long serialVersionUID = 1L;
    private final RDFDetachableModel rdm;
    private String collection;
    
    public NodeProvider() {
        DataCore dc = DataCore.getInstance();
        Dataset ds = dc.getDataset();
        Model h = ModelFactory.createDefaultModel();
        ds.begin(ReadWrite.READ);
        h.add(ds.getNamedModel(HAL.CollectionsAndResources));
        ds.end();
        rdm = new RDFDetachableModel(h);
    }
    
    public Model getRDFModel() {
        return rdm.load();
    }
    
    public String getCollection() {
        return collection;
    }
    
    public void SetSelected(String collection) {
        this.collection = collection;
        UpdateRequest update = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
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
    public Iterator<xNode> getRoots() {
        ArrayList<xNode> ar = new ArrayList<>();
        Model m = rdm.load();
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?s where {
                ?s a so:Dataset; so:hasPart ?o
                minus {?ha so:hasPart ?s}
            }
        """);
        pss.setNsPrefix("so", SchemaDO.NS);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), m).execSelect();
        rs.forEachRemaining(qs->{
            Resource r = qs.getResource("s").asResource();
            ar.add(new xNode(r.asNode(),rdm));
        });
        return ar.iterator();
    }

    @Override
    public boolean hasChildren(xNode node) {
        Model m = rdm.load();
        Resource r = m.createResource(node.getNode().toString());
        boolean hasChildren = m.getProperty(r, SchemaDO.hasPart) != null;
        return hasChildren;
    }

    @Override
    public Iterator<xNode> getChildren(xNode t) {
        Model m = rdm.load();
        ArrayList<xNode> ar = new ArrayList<>();
        NodeIterator ni = m.listObjectsOfProperty(m.asRDFNode(t.getNode()).asResource(), SchemaDO.hasPart);
        while (ni.hasNext()) {
            ar.add(new xNode(ni.nextNode().asNode(),rdm));
        }
        return ar.iterator();
    }

    @Override
    public IModel<xNode> model(xNode foo) {
        return new FooModel(foo);
    }
    
    @Override
    public void detach() {}
    
    private static class FooModel extends LoadableDetachableModel<xNode> {
        private static final long serialVersionUID = 1L;
        private final String id;
        private final xNode local;

        public FooModel(xNode foo) {
            super(foo);
            id = foo.getURI();
            local = foo;
        }

        @Override
        protected xNode load() {
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
