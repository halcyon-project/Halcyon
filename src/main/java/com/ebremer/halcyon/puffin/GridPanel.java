package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.NodeColumn;
import com.ebremer.ethereal.Solution;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 *
 * @author erich
 */
public class GridPanel extends Panel implements IMarkupResourceStreamProvider {

    public GridPanel(String id, IModel<?> model, Resource subject, Node shape) {
        super(id, model);
        HShapes hshapes = new HShapes();
        List<IColumn<Solution, String>> columns = new LinkedList<>();
        ResultSet rs = hshapes.getFormElements(subject, shape);
        
        columns.add(new NodeColumn<>(Model.of("File URI"),"s","s"));
        columns.add(new NodeColumn<>(Model.of("MD5"),"md5","md5"));
        columns.add(new NodeColumn<>(Model.of("width"),"width","width"));
        columns.add(new NodeColumn<>(Model.of("height"),"height","height"));
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
        return new StringResourceStream("""
            <wicket:panel>
                <table wicket:id="table"></table>
            </wicket:panel>
            """);
    }
    
}
