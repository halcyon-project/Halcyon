package com.ebremer.halcyon.gui;

import com.ebremer.vandegraph.LDModel;
import com.ebremer.vandegraph.NodeColumn;
import com.ebremer.vandegraph.RDFDetachableModel;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.DatabaseLocator;
import com.ebremer.vandegraph.RDFTextField;
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
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.Literal;

/**
 * A page for editing a collection, where it creates a form with fields to edit
 * the collection's title and buttons to save or reset changes, and displays a
 * table with access information and actions for associated items
 *
 * @author erich
 */
public class EditCollection extends BasePage {

    private RDFDetachableModel mod;

    public EditCollection(final PageParameters parameters) {
        String uuid = parameters.get("container").toString();
        Resource container = ResourceFactory.createResource(uuid);
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        Model mmm = ModelFactory.createDefaultModel();
        ds.begin(ReadWrite.READ);

        Statement titleStmt = ds.getNamedModel(HAL.CollectionsAndResources).getProperty(container, DCTerms.title);
        if (titleStmt != null) {
            mmm.add(titleStmt);
        } else {
            // Provide a default value if the title is missing
            Literal defaultTitle = mmm.createLiteral("Untitled Collection");
            mmm.add(container, DCTerms.title, defaultTitle);
        }
        ds.end();

        mod = new RDFDetachableModel(mmm);
        LDModel ldm = new LDModel(mod);

        Form form = new Form("yayaya", ldm);
        form.add(new RDFTextField<String>("ContainerName", container, DCTerms.title));
        form.add(new Button("saveButton2") {
            @Override
            public void onSubmit() {
                Dataset ds = DatabaseLocator.getDatabase().getDataset();
                ds.begin(ReadWrite.WRITE);
                try {
                    Model original = mod.loadOriginal();
                    Model updated = mod.load();
                    if (original == null || updated == null) {
                        System.out.println("Original or updated model is null");
                    } else {
                        ds.getNamedModel(HAL.CollectionsAndResources).remove(original);
                        ds.getNamedModel(HAL.CollectionsAndResources).add(updated);
                    }
                    ds.commit();
                } catch (Exception e) {
                    ds.abort();
                    System.out.println(e.getMessage());
                } finally {
                    ds.end();
                }
                setResponsePage(Collections.class);
            }
        }.setDefaultFormProcessing(true));
        form.add(new Button("resetButton") {
            @Override
            public void onSubmit() {
                PageParameters parameters = new PageParameters();
                parameters.add("container", uuid); // Pass the uuid again
                setResponsePage(EditCollection.class, parameters);
            }
        }.setDefaultFormProcessing(false));
        add(form);

        List<IColumn<Solution, String>> columns = new ArrayList<>();
        columns.add(new AbstractColumn<Solution, String>(org.apache.wicket.model.Model.of("Access")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                Solution s = model.getObject();
                int numRead = (int) s.getMap().get("numRead").getLiteralValue();
                int numWrite = (int) s.getMap().get("numWrite").getLiteralValue();
                String d = (numRead > 0) ? "R" : "";
                d = d + ((numWrite > 0) ? "W" : "");
                cellItem.add(new Label(componentId, d));
            }
        });
        columns.add(new AbstractColumn<Solution, String>(org.apache.wicket.model.Model.of("")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new CollectionActionPanel(componentId, model, uuid));
            }
        });
        columns.add(new NodeColumn<>(org.apache.wicket.model.Model.of("Name"), "name", "name"));

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
        if (uuid != null) {
            pss.setIri("item", uuid);
        } else {
            // Throwing an IllegalArgumentException when uuid is null
            throw new IllegalArgumentException("UUID is null, cannot set IRI");
        }
        pss.setNsPrefix("wac", WAC.NS);
        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
        System.out.println(pss.toString());

        SelectDataProvider rdfsdf = new SelectDataProvider(ds, pss.toString());
        rdfsdf.SetSPARQL(pss.toString());

        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 35);
        add(table);
    }
}
