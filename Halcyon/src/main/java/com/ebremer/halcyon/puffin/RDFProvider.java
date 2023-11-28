package com.ebremer.halcyon.puffin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.wicket.extensions.markup.html.repeater.data.table.filter.IFilterStateLocator;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class RDFProvider extends SortableDataProvider<Resource, Node> implements IFilterStateLocator<RDFFilter> {
    private long count;
    private final DetachableResource subject;
    private final DetachableResource property;
    private final Node shape;
    
    public RDFProvider(Resource subject, Property property, Node shape) {
        this.subject = new DetachableResource(subject);
        this.property = new DetachableResource(property);
        this.shape = shape;
        HShapes hshapes = new HShapes();
        ResultSet rs = hshapes.getProperties(shape);
        Query query = QueryFactory.create();
        query.setQuerySelectType();
        query.setDistinct(true);
        Var bn = Var.alloc("bn");
        query.addResultVar(bn);
        ElementGroup body = new ElementGroup();
        ElementTriplesBlock block = new ElementTriplesBlock();
        body.addElement(block);     
        block.addTriple(Triple.create(subject.asNode(), property.asNode(), bn));
        int c=1;
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            String cname = "c"+c;
            Node predicate = qs.get("predicate").asNode();
            Var o = Var.alloc(cname);
            query.addResultVar(o);
            ElementTriplesBlock optionalBlock = new ElementTriplesBlock();
            ElementOptional optionalElement = new ElementOptional(optionalBlock);
            optionalBlock.addTriple(Triple.create(bn, predicate, o));
            body.addElement(optionalElement);
            c++;
        }
        query.setQueryPattern(body);
    }
    
    @Override
    public IModel<Resource> model(Resource object) {
        return new ResourceModel(object);
    }
    
    private void updatecount() {
        StmtIterator i = subject.getObject().listProperties((Property) property.getObject());
        long c=0;
        while (i.hasNext()) {
            i.next();
            c++;
        }
        count=c;
    }

    public Predicate getPredicateInfo(Node name) {
        HShapes hshapes = new HShapes();
        return hshapes.getPredicates(shape).get(name);
    }
    
    public LinkedHashMap<Node,Predicate> getPredicates(Node shape) {
        HShapes hshapes = new HShapes();
        return hshapes.getPredicates(shape);
    }
    
    @Override
    public Iterator<? extends Resource> iterator(long first, long count) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?o where {?s ?p ?o}
            offset ?first limit ?count
        """);
        pss.setIri("s", subject.getObject().getURI());
        pss.setIri("p", property.getObject().getURI());
        pss.setLiteral("first", first);
        pss.setLiteral("count", count);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), subject.getObject().getModel()).execSelect();
        ArrayList<Resource> list = new ArrayList<>((int) count);
        rs.forEachRemaining(qs->{
            Resource o = qs.getResource("o");
            list.add(o);
        });
        return list.iterator();
    }

    @Override
    public long size() {
        updatecount();
        return count;
    }

    @Override
    public void detach() {
        super.detach();
    }

    @Override
    public RDFFilter getFilterState() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setFilterState(RDFFilter state) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
