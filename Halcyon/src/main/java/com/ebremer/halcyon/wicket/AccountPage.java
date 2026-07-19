package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.lws.client.LwsClient;
import com.ebremer.halcyon.lws.LwsDatasets;
import com.ebremer.halcyon.server.ColorClassesStore;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.vandegraph.SelectDataProvider;
import com.ebremer.vandegraph.Solution;
import com.ebremer.vandegraph.SparqlVarColumn;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /user/account} — the signed-in user's own account page (replacing
 * the old Keycloak account-console iframe, which is now a plain link out).
 *
 * <p>Three sections, all scoped to THE CALLER:
 * <ul>
 *   <li><b>Identity</b> — the session principal as Halcyon uses it: WebID
 *       (the identity every ACP policy and creator stamp names), preferred
 *       username, display name, groups.</li>
 *   <li><b>Annotation color classes</b> — the user's palette (their LWS
 *       {@code colorclasses.ttl}), swatches inline, with the editor a click
 *       away.</li>
 *   <li><b>Your files</b> — a vandegraph {@link SelectDataProvider} table
 *       (SPARQL-side paging/sorting) over the user's personal areas
 *       ({@code {storage}/users/{name}/…} in every configured storage),
 *       queried through the caller's own ACP-secured view of the LWS store
 *       ({@link LwsDatasets}) — so this page can only ever show what the
 *       storage itself would answer to this user.</li>
 * </ul>
 */
public class AccountPage extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(AccountPage.class);
    private static final String AS_NS = "https://www.w3.org/ns/activitystreams#";

    public AccountPage() {
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        String user = hp.getPreferredUserName();

        // --- Identity ---------------------------------------------------------
        add(new Label("username", String.valueOf(user)));
        add(new Label("fullname", String.valueOf(hp.getName())));
        add(new Label("webid", String.valueOf(hp.getUserURI())));
        add(new Label("groups", hp.getGroups() == null || hp.getGroups().isEmpty()
                ? "(none)" : String.join(", ", hp.getGroups())));
        add(new ExternalLink("keycloak",
                "/auth/realms/" + HalcyonSettings.REALM + "/account",
                "Manage profile & credentials (Keycloak) ↗"));

        // --- Annotation color classes ------------------------------------------
        List<ColorClassesStore.Row> classes = loadColorClasses(hp, user);
        add(new Label("noclasses",
                "No color classes defined yet — the annotation palette uses its defaults.")
                .setVisible(classes.isEmpty()));
        add(new ListView<ColorClassesStore.Row>("classes", classes) {
            @Override
            protected void populateItem(ListItem<ColorClassesStore.Row> item) {
                ColorClassesStore.Row row = item.getModelObject();
                WebMarkupContainer swatch = new WebMarkupContainer("swatch");
                swatch.add(AttributeModifier.replace("style",
                        "background-color: " + safeColor(row.color()) + ";"));
                item.add(swatch);
                item.add(new Label("cname", row.name()));
            }
        });
        String host = HalcyonSettings.getSettings().getProxyHostName();
        add(new ExternalLink("editclasses", host + "/user/colorclasses", "Edit color classes"));

        // --- Your files (vandegraph table over the caller's secured view) ------
        List<String> prefixes = userAreaPrefixes(user);
        WebMarkupContainer files = new WebMarkupContainer("filesSection");
        files.setVisible(!prefixes.isEmpty());
        add(files);
        if (!prefixes.isEmpty()) {
            ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                select ?g ?mediaType ?size ?modified
                where {
                    graph ?g {
                        ?g as:updated ?modified .
                        optional { ?g as:mediaType ?mediaType }
                        optional { ?g sdo:size ?size }
                    }
                    values ?prefix { %s }
                    filter(strstarts(str(?g), ?prefix))
                } order by ?g
                """.formatted(valuesList(prefixes)));
            pss.setNsPrefix("sdo", "https://schema.org/");
            pss.setNsPrefix("as", AS_NS);

            SelectDataProvider provider =
                    new SelectDataProvider(LwsDatasets.securedForSession(), pss.toString());
            List<IColumn<Solution, String>> columns = new ArrayList<>();
            columns.add(new SparqlVarColumn(org.apache.wicket.model.Model.of("URI"), "g"));
            columns.add(new SparqlVarColumn(org.apache.wicket.model.Model.of("Media type"), "mediaType"));
            columns.add(new SparqlVarColumn(org.apache.wicket.model.Model.of("Size"), "size"));
            columns.add(new SparqlVarColumn(org.apache.wicket.model.Model.of("Modified"), "modified"));
            files.add(new AjaxFallbackDefaultDataTable<>("files", columns, provider, 25));
        } else {
            files.add(new WebMarkupContainer("files"));
        }
    }

    /** The user's palette, read with their own token; empty on any failure. */
    private static List<ColorClassesStore.Row> loadColorClasses(HalcyonPrincipal hp, String user) {
        try {
            LwsStorageConfig cfg = ColorClassesStore.storage();
            if (cfg == null || user == null) {
                return List.of();
            }
            String uri = ColorClassesStore.documentUri(cfg, user);
            LwsClient c = new LwsClient(hp.isAnon() ? null : hp.getToken(),
                    HalcyonSettings.getSettings().getProxyHostName());
            LwsClient.Text t = c.getText(uri, "text/turtle");
            if (!t.ok()) {
                return List.of();
            }
            Model m = ModelFactory.createDefaultModel();
            RDFDataMgr.read(m, new StringReader(t.body() == null ? "" : t.body()), uri, Lang.TURTLE);
            return ColorClassesStore.rows(m);
        } catch (RuntimeException ex) {
            logger.debug("color classes unavailable for the account page: {}", ex.toString());
            return List.of();
        }
    }

    /** The user's personal-area prefix in every configured storage. */
    private static List<String> userAreaPrefixes(String user) {
        List<String> out = new ArrayList<>();
        if (user == null || user.isBlank()) {
            return out;
        }
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            out.add(cfg.baseUri() + "/users/" + pathSegment(user) + "/");
        }
        return out;
    }

    /** SPARQL VALUES literals for the prefixes (quoted; they contain no quotes). */
    private static String valuesList(List<String> prefixes) {
        StringBuilder sb = new StringBuilder();
        for (String p : prefixes) {
            sb.append('"').append(p.replace("\\", "").replace("\"", "")).append("\" ");
        }
        return sb.toString().trim();
    }

    /** A CSS color that cannot break out of the style attribute. */
    private static String safeColor(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{3,8}") ? color : "#888888";
    }

    private static String pathSegment(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by the JDK", e);
        }
    }
}
