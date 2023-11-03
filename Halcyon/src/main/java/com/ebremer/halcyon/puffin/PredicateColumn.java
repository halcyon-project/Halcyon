package com.ebremer.halcyon.puffin;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.IExportableColumn;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;

public class PredicateColumn extends AbstractColumn<Resource, Node> implements IExportableColumn<Resource, Node> {
    private static final long serialVersionUID = 1L;
    private final Node predicate;
    private final RDFProvider rp;
//    private final String name;

    //public PredicateColumn(final IModel<String> displayModel, String name, final Node sortProperty, final Node predicate, RDFProvider rp) {
    public PredicateColumn(final IModel<String> displayModel, final Node sortProperty, final Node predicate, RDFProvider rp) {
        super(displayModel, sortProperty);
        this.predicate = predicate;
        this.rp = rp;
       // this.name = name;
    }

    @Override
    public void populateItem(final Item<ICellPopulator<Resource>> cellItem, final String componentId, final IModel<Resource> resourceModel) {
        Model k = resourceModel.getObject().getModel();
        Property pp = k.createProperty(predicate.getURI());
        if (resourceModel.getObject().hasProperty(pp)) {
            Statement statement = resourceModel.getObject().getProperty(pp);
            Predicate p = rp.getPredicateInfo(predicate);
            Component c = new RDFPanel(componentId, statement, p);
            cellItem.add(c);
        } else {
            cellItem.add(new EmptyPanel(componentId));
        }
    }

    @Override
    public IModel<?> getDataModel(IModel<Resource> rowModel) {
        return new ResourceModel(rowModel.getObject());
    }
}
