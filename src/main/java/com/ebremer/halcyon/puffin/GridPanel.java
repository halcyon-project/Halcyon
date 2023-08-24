package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.NodeColumn;
import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.ethereal.Solution;
import com.ebremer.ns.HAL;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 *
 * @author erich
 */
public class GridPanel extends Panel implements IMarkupResourceStreamProvider {

    public GridPanel(String id, Resource subject, Property property, Node shape) {
        super(id);
        HShapes hshapes = new HShapes();
        List<IColumn<Solution, String>> columns = new LinkedList<>();
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
            Var o = Var.alloc(cname);
            query.addResultVar(o);
            ElementTriplesBlock optionalBlock = new ElementTriplesBlock();
            ElementOptional optionalElement = new ElementOptional(optionalBlock);
            optionalBlock.addTriple(Triple.create(bn, qs.get("predicate").asNode(), o));
            body.addElement(optionalElement);
            String name = qs.contains("name")?qs.getLiteral("name").getString():qs.getResource("predicate").getLocalName();
            columns.add(new NodeColumn<>(Model.of(name),cname,cname));
            c++;
        }
        query.setQueryPattern(body);
        System.out.println(query.serialize(Syntax.syntaxSPARQL));
        Dataset ds = DatasetFactory.createTxnMem();
        ds.setDefaultModel(subject.getModel());
        SelectDataProvider rdfsdf = new SelectDataProvider(ds,query.serialize(Syntax.syntaxSPARQL));
        rdfsdf.SetSPARQL(query.serialize(Syntax.syntaxSPARQL));
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 25);
        add(table);
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
        return new StringResourceStream("""
            <wicket:panel>
                <table wicket:id="table"></table>
            </wicket:panel>
        """);
    }
}
