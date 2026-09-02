package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.lws.LwsDatasets;
import com.ebremer.halcyon.wicket.ethereal.Zephyr;
import com.ebremer.ns.ZEPH;
import com.ebremer.vandegraph.SelectDataProvider;
import com.ebremer.vandegraph.Solution;
import com.ebremer.vandegraph.SparqlVarColumn;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 * The Stacks list, W3C-LWS-native and vandegraph-tabled — the read-only twin of
 * {@link Images}, showing every {@code zeph:Stack}-typed resource across the
 * configured storages THAT THIS CALLER MAY READ. It queries the caller's own
 * ACP-secured view of the store ({@link LwsDatasets}) with SPARQL, so an
 * unauthorized stack is not merely hidden — it is not discoverable, the same
 * Type Search guarantee the Images list inherits. Paging and header sorting are
 * SPARQL-side via vandegraph's {@link SelectDataProvider}, re-queried every render.
 *
 * <p>Read-only: a row opens the stack in {@link Zephyr} ({@code OPEN_STACK}), where
 * it can be edited and saved back to its storage. Deletion is done from the LWS
 * Containers browser, where a stack is just another resource. The triple-store
 * stack era is over here — no named graphs, no {@code StackStore}.
 */
public class Stacks extends BasePage {

    public Stacks() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?g ?name ?mediaType ?size
            where {
                graph ?g {
                    ?g a zeph:Stack .
                    optional { ?g sdo:name ?name }
                    optional { ?g as:mediaType ?mediaType }
                    optional { ?g sdo:size ?size }
                }
            } order by ?g
            """);
        pss.setNsPrefix("zeph", ZEPH.NS);
        pss.setNsPrefix("sdo", SchemaDO.NS);
        pss.setNsPrefix("as", "https://www.w3.org/ns/activitystreams#");

        SelectDataProvider provider =
                new SelectDataProvider(LwsDatasets.securedForSession(), pss.toString());

        List<IColumn<Solution, String>> columns = new ArrayList<>();
        columns.add(new AbstractColumn<Solution, String>(Model.of("Name"), "g") {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId,
                    IModel<Solution> rowModel) {
                cellItem.add(new OpenPanel(componentId, rowModel));
            }
        });
        columns.add(new SparqlVarColumn(Model.of("Title"), "name"));
        columns.add(new SparqlVarColumn(Model.of("Media type"), "mediaType"));
        columns.add(new SparqlVarColumn(Model.of("Size"), "size"));
        columns.add(new SparqlVarColumn(Model.of("URI"), "g"));

        add(new AjaxFallbackDefaultDataTable<>("stacks", columns, provider, 25));
    }

    /** Name cell: opens the stack in Zephyr for viewing/editing. */
    private static final class OpenPanel extends Panel implements IMarkupResourceStreamProvider {

        private OpenPanel(String id, IModel<Solution> model) {
            super(id);
            Node g = model.getObject().get("g");
            String uri = g != null && g.isURI() ? g.getURI() : "";
            Link<Void> open = new Link<>("open") {
                @Override
                public void onClick() {
                    setResponsePage(new Zephyr(uri, Zephyr.Mode.OPEN_STACK));
                }
            };
            open.add(new Label("name", tail(uri)));
            add(open);
        }

        private static String tail(String uri) {
            String s = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
            int i = s.lastIndexOf('/');
            return i >= 0 && i < s.length() - 1 ? s.substring(i + 1) : s;
        }

        @Override
        public IResourceStream getMarkupResourceStream(MarkupContainer container,
                Class<?> containerClass) {
            return new StringResourceStream("""
                <wicket:panel xmlns:wicket="http://wicket.apache.org">
                <a wicket:id="open"><span wicket:id="name"></span></a>
                </wicket:panel>
                """);
        }
    }
}
