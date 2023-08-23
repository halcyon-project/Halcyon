package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.NodeColumn;
import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.ethereal.Solution;
import com.ebremer.ns.HAL;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
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
        rs.forEachRemaining(qs->{
            columns.add(new NodeColumn<>(Model.of("File URI"),"s","s"));
        });
        
        columns.add(new NodeColumn<>(Model.of("hasClass"),"hasClass","hasClass"));
        columns.add(new NodeColumn<>(Model.of("color"),"color","color"));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?color ?hasClass
            where { ?subject hal:hasAnnotationClass ?ac . ?ac hal:color ?color; hal:hasClass ?hasClass }
            """
        );
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("subject", subject.getURI());
        Dataset ds = DatasetFactory.createTxnMem();
        ds.setDefaultModel(subject.getModel());
        System.out.println("===================================================");
        System.out.println(pss.toString());
        System.out.println("===================================================");
        SelectDataProvider rdfsdf = new SelectDataProvider(ds,pss.toString());
        rdfsdf.SetSPARQL(pss.toString());
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
