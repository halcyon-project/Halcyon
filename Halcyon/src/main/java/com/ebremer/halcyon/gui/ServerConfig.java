package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.PropertyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /admin} — the Halcyon instance configuration page (replacing the old
 * Keycloak-console iframe, which is now a plain link out).
 *
 * <p>Two halves. The TOP is the EFFECTIVE configuration — what this running
 * instance actually loaded at startup from {@code settings.ttl}, shown
 * read-only so an owner can see at a glance what is live. The BOTTOM is the
 * settings file itself in a raw Turtle editor: every setting is editable,
 * including ones no form knows about yet. A save is gated on the text
 * PARSING as Turtle (a file that cannot parse would brick the next boot),
 * the previous file is kept as a timestamped {@code .bak} beside it, and the
 * write is atomic (temp + move). Settings are read once at startup, so the
 * page says plainly: restart to apply.
 */
public class ServerConfig extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(ServerConfig.class);

    /** Same resolution rule as HalcyonSettings/LwsSettings: relative to the CWD. */
    private static final Path SETTINGS = Path.of("settings.ttl");

    private String rawTtl;
    private String message = "";

    public ServerConfig() {
        HalcyonSettings hs = HalcyonSettings.getSettings();

        // --- The effective configuration, as loaded at startup ---------------
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Settings file", SETTINGS.toAbsolutePath().toString()});
        rows.add(new String[]{"Host name", String.valueOf(hs.getHostName())});
        rows.add(new String[]{"Proxy host name", String.valueOf(hs.getProxyHostName())});
        rows.add(new String[]{"HTTP / HTTPS port", hs.GetHTTPPort() + " / " + hs.GetHTTPSPort()});
        rows.add(new String[]{"SPARQL port (loopback Fuseki)", String.valueOf(hs.GetSPARQLPort())});
        rows.add(new String[]{"Mode", hs.isDevMode() ? "dev" : "release"});
        rows.add(new String[]{"Auth server", String.valueOf(hs.getAuthServer())});
        rows.add(new String[]{"Realm", String.valueOf(hs.getRealm())});
        rows.add(new String[]{"Classic RDF store", String.valueOf(hs.getRDFStoreLocation())});
        rows.add(new String[]{"LWS RDF store", String.valueOf(LwsSettings.get().storeLocation())});
        rows.add(new String[]{"LWS owner (WebID)", String.valueOf(LwsSettings.get().owner())});
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            StringBuilder v = new StringBuilder(cfg.contentRoot() + "  (" + cfg.naming() + " naming)");
            cfg.mounts().forEach(mt ->
                    v.append("  |  mount ").append(mt.containerPath()).append(" -> ").append(mt.root()));
            rows.add(new String[]{"LWS storage " + cfg.urlPath(), v.toString()});
        }
        LwsStorageConfig ud = LwsSettings.get().userDataStorage();
        rows.add(new String[]{"LWS user-data storage", ud == null ? "(none)" : ud.urlPath()});
        rows.add(new String[]{"Zephyr location override", String.valueOf(hs.getZephyrLocation())});
        rows.add(new String[]{"File scan disabled", String.valueOf(hs.IsFileScanDisabled())});

        add(new ListView<String[]>("rows", rows) {
            @Override
            protected void populateItem(ListItem<String[]> item) {
                item.add(new Label("key", item.getModelObject()[0]));
                item.add(new Label("value", item.getModelObject()[1]));
            }
        });

        // The user/group administration this page used to iframe.
        add(new ExternalLink("keycloak",
                "/auth/admin/" + HalcyonSettings.REALM + "/console/",
                "Open Keycloak administration ↗"));

        // --- The settings file editor ----------------------------------------
        rawTtl = readFile();
        Form<Void> form = new Form<>("form");
        add(form);
        form.add(new TextArea<>("raw", new PropertyModel<>(this, "rawTtl")));
        form.add(new Button("save") {
            @Override
            public void onSubmit() {
                save();
            }
        });
        form.add(new Button("reload") {
            @Override
            public void onSubmit() {
                rawTtl = readFile();
                message = "Reloaded from disk; unsaved edits discarded.";
            }
        }.setDefaultFormProcessing(false));
        add(new Label("message", new PropertyModel<>(this, "message")));
    }

    private String readFile() {
        try {
            return Files.readString(SETTINGS, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            message = "Could not read " + SETTINGS.toAbsolutePath() + ": " + ex.getMessage();
            return "";
        }
    }

    /**
     * Validate, back up, write atomically. The parse gate is the whole point:
     * a settings file that does not parse would brick the next boot, so it
     * never reaches disk.
     */
    private void save() {
        String text = rawTtl == null ? "" : rawTtl;
        Model m = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(m, new StringReader(text), null, Lang.TURTLE);
        } catch (RuntimeException ex) {
            message = "NOT saved — the file must parse as Turtle: " + ex.getMessage();
            return;
        }
        String warning = m.contains(null, org.apache.jena.vocabulary.RDF.type,
                m.createResource("https://halcyon.is/ns/HalcyonSettingsFile"))
                ? "" : " WARNING: no :HalcyonSettingsFile subject — is this really a settings file?";
        try {
            Path backup = SETTINGS.resolveSibling("settings-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + ".ttl.bak");
            if (Files.exists(SETTINGS)) {
                Files.copy(SETTINGS, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Path tmp = SETTINGS.resolveSibling("settings.ttl.tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, SETTINGS, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, SETTINGS, StandardCopyOption.REPLACE_EXISTING);
            }
            message = "Saved (backup: " + backup.getFileName() + "). "
                    + "Settings load at startup — RESTART Halcyon to apply." + warning;
            logger.info("settings.ttl updated by an admin; backup {}", backup.getFileName());
        } catch (IOException ex) {
            message = "NOT saved — " + ex.getMessage();
        }
    }
}
