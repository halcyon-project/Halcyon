package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.wicket.ethereal.Zephyr3;
import com.ebremer.ns.ZEPH;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists every {@code zeph:Stack} found across the triple store's named graphs —
 * both stacks discovered from {@code *.jsonld} files and stacks saved from the
 * Zephyr viewer. Each row opens the stack in {@link Zephyr3}.
 */
public class Stacks extends BasePage {
    private static final Logger logger = LoggerFactory.getLogger(Stacks.class);

    public Stacks() {
        add(new ListView<StackRow>("rows", loadStacks()) {
            @Override
            protected void populateItem(ListItem<StackRow> item) {
                StackRow row = item.getModelObject();
                Link<Void> view = new Link<Void>("view") {
                    @Override
                    public void onClick() {
                        setResponsePage(new Zephyr3(row.graph(), Zephyr3.Mode.OPEN_STACK));
                    }
                };
                view.add(new Label("name", row.label()));
                item.add(view);
                item.add(new Label("uri", row.subject()));
            }
        });
    }

    private List<StackRow> loadStacks() {
        List<StackRow> rows = new ArrayList<>();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?s ?g ?name where {
                graph ?g { ?s a zeph:Stack . optional { ?s sdo:name ?name } }
            } order by ?name ?s
            """);
        pss.setNsPrefix("zeph", ZEPH.NS);
        pss.setNsPrefix("sdo", SchemaDO.NS);
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                String s = qs.get("s").isResource() ? qs.getResource("s").getURI() : qs.get("s").toString();
                String g = qs.getResource("g").getURI();
                String name = qs.contains("name") ? qs.getLiteral("name").getString() : shortName(s);
                rows.add(new StackRow(s, g, name));
            }
        } catch (Exception ex) {
            logger.error("Failed to list stacks", ex);
        } finally {
            ds.end();
        }
        return rows;
    }

    private static String shortName(String uri) {
        if (uri == null || uri.isEmpty()) return "(unnamed stack)";
        int i = Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('#'));
        return (i >= 0 && i < uri.length() - 1) ? uri.substring(i + 1) : uri;
    }

    public record StackRow(String subject, String graph, String label) {}
}
