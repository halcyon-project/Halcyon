package com.ebremer.halcyon.puffin;

import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 *
 * @author erich
 */
public class RDFPanel extends Panel implements IMarkupResourceStreamProvider {

    public RDFPanel(String id, Statement s, Predicate p) {
        super(id);
        setOutputMarkupId(true);
        add(ComponentBuilder.BuildComponentPanel("inputfield", s, p));
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
        return new StringResourceStream(
            """
            <wicket:panel>
                <div wicket:id="inputfield"/>
            </wicket:panel>"
            """);
    }
}
