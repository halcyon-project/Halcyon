package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.lws.LwsCommandNode;
import com.ebremer.halcyon.server.ColorClassesStore;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.ns.HAL;
import com.ebremer.vandegraph.shacl.SHACLForm;
import com.ebremer.vandegraph.workspace.WorkspaceModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.wicket.markup.html.basic.Label;

/**
 * Per-user annotation color-class editor: the same {@code SHACLForm} over the
 * user's {@code hal:AnnotationClassList} as always — but the list now lives
 * in the W3C LWS storage as the user's own relative document
 * ({@code {storage}/users/{name}/colorclasses.ttl}), loaded and saved through
 * {@link LwsCommandNode} with the session's own token, so ACP's creator
 * policy makes the document theirs and a concurrent edit surfaces as the
 * form's usual conflict flow.
 */
public class ColorClasses extends BasePage {
    private static final long serialVersionUID = 1L;

    public ColorClasses() {
        String user = HalcyonSession.get().getUser();
        LwsStorageConfig cfg = ColorClassesStore.storage();
        if (cfg == null) {
            add(new Label("sform", "No W3C LWS storage is configured for user data "
                    + "(settings.ttl :hasLWSStorage / :LWSUserDataStorage), "
                    + "so color classes cannot be edited."));
            return;
        }
        String docUri = ColorClassesStore.documentUri(cfg, user);

        LwsCommandNode cn = new LwsCommandNode(docUri);
        Model working = cn.workspaceLoadOrSeed(
                HalcyonSession.get().getWorkspace(),
                () -> ColorClassesStore.emptyList(docUri));

        // The buffer may predate color classes — make sure it carries a list
        // resource the shape can anchor on.
        Resource key;
        if (working.contains(null, RDF.type, HAL.AnnotationClassList)) {
            key = working.listSubjectsWithProperty(RDF.type, HAL.AnnotationClassList)
                    .next();
        } else {
            key = working.createResource(docUri);
            key.addProperty(RDF.type, HAL.AnnotationClassList);
        }

        add(new SHACLForm("sform",
                new WorkspaceModel(docUri),
                key,
                HAL.AnnotationClassListShape.asNode(),
                cn));
    }
}
