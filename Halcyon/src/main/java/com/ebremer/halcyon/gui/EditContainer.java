package com.ebremer.halcyon.gui;

import com.ebremer.vandegraph.SparqlVarColumn;
import com.ebremer.vandegraph.SessionScopedModel;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.DatabaseLocator;
import com.ebremer.vandegraph.SelectDataProvider;
import com.ebremer.vandegraph.Solution;
import com.ebremer.ns.HAL;
import com.ebremer.ns.WAC;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import com.ebremer.vandegraph.PropertyValueModel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 *
 * @author erich
 */
public class EditContainer extends BasePage {
    private final String uuid;
    private SessionScopedModel mod;
    
    public EditContainer(final PageParameters parameters) {
        uuid = parameters.get("collection").toString();
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        Model m = ModelFactory.createDefaultModel();
        ds.begin(ReadWrite.READ);
        m.add(ds.getNamedModel(uuid));
        ds.end();
        Resource s = ResourceFactory.createResource(uuid);
        mod = new SessionScopedModel(m);
        Form<Void> form = new Form<>("yayaya");
        form.add(new TextField<>("CollectionName", PropertyValueModel.of(mod, s, SchemaDO.name)));
        form.add(new Button("saveButton2") {
            @Override
            public void onSubmit() {
                Model after = mod.getObject();
                Dataset ds = DatabaseLocator.getDatabase().getDataset();
                ds.begin(ReadWrite.WRITE);
                if (ds.containsNamedModel(uuid)) {
                    System.out.println("REMOVE OLD GRAPH "+uuid);
                    ds.removeNamedModel(uuid);
                } else {
                    System.out.println("DOESNT EXIST OLD GRAPH "+uuid);
                }
                ds.addNamedModel(uuid, after);
                ds.commit();
                ds.end();
                setResponsePage(Collections.class);
            }}.setDefaultFormProcessing(true)
        );
        form.add(new Button("resetButton") {
            @Override
            public void onSubmit() {
                setResponsePage(EditContainer.class);
            }}.setDefaultFormProcessing(false)
        );
        add(form);
        List<IColumn<Solution, String>> columns = new ArrayList<>();
        columns.add(new AbstractColumn<Solution, String>(org.apache.wicket.model.Model.of("Access")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                Solution s = model.getObject();
                int numRead = (int) s.get("numRead").getLiteralValue();
                int numWrite = (int) s.get("numWrite").getLiteralValue();
                String d = (numRead>0) ? "R":"";
                d = d + ((numWrite>0) ? "W":"");
                cellItem.add(new Label(componentId, d));
            }
        });
        columns.add(new AbstractColumn<Solution, String>(org.apache.wicket.model.Model.of("")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new CollectionActionPanel(componentId, model, uuid));
            }
        });
        columns.add(new SparqlVarColumn(org.apache.wicket.model.Model.of("Name"), "name"));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?s ?name (count(distinct ?aclRead) as ?numRead) (count(distinct ?aclWrite) as ?numWrite)
            where {
                  graph ?GroupsAndUsers {?s a so:Organization; so:name ?name}
                  optional {graph ?SecurityGraph {?aclRead wac:accessTo ?item; wac:agent ?s; wac:mode wac:Read}}
                  optional {graph ?SecurityGraph {?aclWrite wac:accessTo ?item; wac:agent ?s; wac:mode wac:Write}}
                  }
            group by ?name ?s
            order by ?name ?s
            """
        );
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("GroupsAndUsers", HAL.GroupsAndUsers.getURI());
        pss.setIri("item", uuid);
        pss.setNsPrefix("wac", WAC.NS);
        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
        System.out.println(pss.toString());
        SelectDataProvider rdfsdf = new SelectDataProvider(ds,pss.toString());
        rdfsdf.setQuery(pss.toString());
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 35);
        add(table);
    }
}
