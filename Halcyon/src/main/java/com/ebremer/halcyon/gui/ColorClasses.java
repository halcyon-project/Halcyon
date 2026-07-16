package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.ns.HAL;
import com.ebremer.vandegraph.shacl.SHACLForm;
import com.ebremer.vandegraph.workspace.CommandNode;
import com.ebremer.vandegraph.workspace.WorkspaceModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Per-user annotation color-class editor: a {@code SHACLForm} over the
 * user's {@code hal:AnnotationClassList}, working against a session
 * buffer of the user's named graph and saved back through
 * {@link CommandNode} (validation-gated, conflict-checked).
 */
public class ColorClasses extends BasePage {
    private static final long serialVersionUID = 1L;

    public ColorClasses() {
        String host = HalcyonSettings.getSettings().getHostName();
        String user = HalcyonSession.get().getUser();
        String listUri = host + "/users/" + user + "/colorclasses";
        String graphUri = host + "/users/" + user + "/";

        CommandNode cn = new CommandNode(graphUri);
        Model working = cn.workspaceLoadOrSeed(
                HalcyonSession.get().getWorkspace(),
                () -> {
                    Model seed = ModelFactory.createDefaultModel();
                    seed.createResource(listUri)
                            .addProperty(RDF.type, HAL.AnnotationClassList);
                    return seed;
                });

        // The user's graph may predate color classes — make sure it carries
        // a list resource the shape can anchor on.
        Resource key;
        if (working.contains(null, RDF.type, HAL.AnnotationClassList)) {
            key = working.listSubjectsWithProperty(RDF.type, HAL.AnnotationClassList)
                    .next();
        } else {
            key = working.createResource(listUri);
            key.addProperty(RDF.type, HAL.AnnotationClassList);
        }

        add(new SHACLForm("sform",
                new WorkspaceModel(graphUri),
                key,
                HAL.AnnotationClassListShape.asNode(),
                cn));
    }
}
