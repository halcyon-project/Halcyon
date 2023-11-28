package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 *
 * @author erich
 */
public class NodeViewer extends Panel implements IMarkupResourceStreamProvider {
    
    public NodeViewer(String id, RDFDetachableModel mod, Resource r, Node shape) {
        super(id);
        
    }
    
    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><wicket:panel><form wicket:id=\"form\">")
            .append("<input type=\"submit\" wicket:id=\"saveButton\" value=\"save\" />")
            .append("<input type=\"submit\" wicket:id=\"deleteButton\" value=\"delete\" />")
            .append("<input type=\"submit\" wicket:id=\"resetButton\" value=\"reset\" />")
            .append("<select wicket:id=\"predicates\">Predicates</select>")
            .append("<div wicket:id=\"predicateObjectRepeatingView\"><label wicket:id=\"predicatename\"/><label wicket:id=\"predicatemessages\"/><div wicket:id=\"childRepeatingView\"></div></div>")
            .append("</form></wicket:panel></body></html>");
        return new StringResourceStream(sb.toString());
    }
}
