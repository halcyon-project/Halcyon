package com.ebremer.halcyon.puffin;

import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;

/**
 *
 * @author erich
 */
public class RDFDataTable extends AjaxFallbackDefaultDataTable<Resource,Node> {

    public RDFDataTable(String id, List<? extends IColumn<Resource, Node>> columns, ISortableDataProvider<Resource, Node> dataProvider, int rowsPerPage) {
        super(id, columns, dataProvider, rowsPerPage);
    }
}
