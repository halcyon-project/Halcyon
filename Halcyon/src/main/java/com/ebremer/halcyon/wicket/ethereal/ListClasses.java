package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.ethereal.NodeColumn;
import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.ethereal.Solution;
import com.ebremer.halcyon.wicket.DatabaseLocator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

public class ListClasses extends Panel {
    private static final long serialVersionUID = 1L;
    private final SelectDataProvider rdfsdf;
    private final HashSet<String> selected;
    
    public ListClasses(String id) {
        super(id);
        selected = new HashSet<>();
        List<IColumn<Solution, String>> columns = new ArrayList<>();
        columns.add(new AbstractColumn<Solution, String>(Model.of("")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new ActionPanel(componentId, model));
            }
        });
        columns.add(new NodeColumn<>(Model.of("Predicate"),"p","p"));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?p
            where {graph ?g {?s ?p ?o}}
            order by ?p
            """);
        pss.setNsPrefix("so", SchemaDO.NS);
        //Dataset ds = DatabaseLocator.getDatabase().getSecuredDataset();
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        ds.begin(ReadWrite.READ);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        ResultSetFormatter.out(System.out,qe.execSelect());
        ds.end();
        rdfsdf = new SelectDataProvider(ds,pss.toString());
        rdfsdf.SetSPARQL(pss.toString());        
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 35);
        add(table);
    }
    
    public List<RDFNode> getClasses() {
        List<RDFNode> wow = new LinkedList<>();
        selected.forEach(k->{
            wow.add(ModelFactory.createDefaultModel().asRDFNode(ResourceFactory.createResource(k).asNode()));
        });
        return wow;
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(ListClasses.class, "repeater.css")));
    }
    
    private class ActionPanel extends Panel implements IMarkupResourceStreamProvider {

        public ActionPanel(String id, IModel<Solution> model) {
            super(id, model);
            String key = model.getObject().getMap().get("p").toString();
            CheckBox ds1 = new CheckBox("checkbox", Model.of(selected.contains(key)));
            ds1.add(new OnChangeAjaxBehavior() {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    if (Boolean.parseBoolean(ds1.getValue())) {
                        if (!selected.contains(key)) {
                            selected.add(key);
                        }
                    } else {
                        if (selected.contains(key)) {
                            selected.remove(key);
                        }                        
                    }
                }
            });
            add(ds1);
        }
        
        @Override
        public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
            return new StringResourceStream("""
                <wicket:panel xmlns:wicket="http://wicket.apache.org">
                <input type="checkbox" wicket:id="checkbox" />
                </wicket:panel>
            """);
        }
    }
}