package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.lws.LwsDatasets;
import com.ebremer.halcyon.wicket.ethereal.Zephyr;
import com.ebremer.ns.EXIF;
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
 * The Images list, W3C-LWS-native and vandegraph-tabled: every
 * {@code schema:ImageObject}-typed resource across the configured storages
 * THAT THIS CALLER MAY READ — the metadata the LWS scanner derived (type,
 * pixel dimensions) plus what the storage records (media type, size), queried
 * with SPARQL through the caller's own ACP-secured view of the store
 * ({@link LwsDatasets}). Paging and header sorting are SPARQL-side via
 * vandegraph's {@link SelectDataProvider}, re-queried every render.
 *
 * <p>Feature sets are excluded here on the same line the old screens drew:
 * a BeakGraph types itself {@code schema:ImageObject} AND
 * {@code schema:Dataset}, and anything carrying the Dataset type is a
 * feature file, not an image. Clicking a name seeds a fresh Zephyr stack
 * with the image as layer 0 — the same door the LWS Containers preview uses.
 */
public class Images extends BasePage {

    private static final String AS_NS = "https://www.w3.org/ns/activitystreams#";

    public Images() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?g ?width ?height ?mediaType ?size
            where {
                graph ?g {
                    ?g a sdo:ImageObject .
                    filter not exists { ?g a sdo:Dataset }
                    optional { ?g exif:width ?width }
                    optional { ?g exif:height ?height }
                    optional { ?g as:mediaType ?mediaType }
                    optional { ?g sdo:size ?size }
                }
            } order by ?g
            """);
        pss.setNsPrefix("sdo", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("as", AS_NS);

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
        columns.add(new SparqlVarColumn(Model.of("Width"), "width"));
        columns.add(new SparqlVarColumn(Model.of("Height"), "height"));
        columns.add(new SparqlVarColumn(Model.of("Media type"), "mediaType"));
        columns.add(new SparqlVarColumn(Model.of("Size"), "size"));
        columns.add(new SparqlVarColumn(Model.of("URI"), "g"));

        add(new AjaxFallbackDefaultDataTable<>("images", columns, provider, 25));
    }

    /** Name cell: opens the image as layer 0 of a fresh Zephyr stack. */
    private static final class OpenPanel extends Panel implements IMarkupResourceStreamProvider {

        private OpenPanel(String id, IModel<Solution> model) {
            super(id);
            Node g = model.getObject().get("g");
            String uri = g != null && g.isURI() ? g.getURI() : "";
            Link<Void> open = new Link<>("open") {
                @Override
                public void onClick() {
                    setResponsePage(new Zephyr(uri));
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
