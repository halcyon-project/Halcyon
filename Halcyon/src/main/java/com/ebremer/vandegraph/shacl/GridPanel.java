package com.ebremer.vandegraph.shacl;

import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.apache.wicket.request.resource.PackageResourceReference;

/**
 *
 * @author erich
 */
public class GridPanel extends Panel implements IMarkupResourceStreamProvider {

    private final DetachableResource subject;
    private final DetachableResource property;
    private final RDFDataTable table;
    private static final Logger logger = Logger.getLogger(GridPanel.class.getName());

    public GridPanel(String id, Resource subject, Property property, Node shape) {
        super(id);
        this.subject = new DetachableResource(subject);
        this.property = new DetachableResource(property);
        RDFProvider rp = new RDFProvider(subject, property, shape);
        List<AbstractColumn<Resource, Node>> columns = new ArrayList<>();
        rp.getPredicates(shape).forEach((k, v) -> {
            columns.add(new PredicateColumn(Model.of(v.name()), v.node(), v.node(), rp));
        });
        columns.add(new AbstractColumn<Resource, Node>(Model.of("")) {
            @Override
            public void populateItem(Item<ICellPopulator<Resource>> cellItem, String componentId, IModel<Resource> model) {
                cellItem.add(new ActionPanel(componentId, model));
            }
        });
        table = new RDFDataTable("table", columns, rp, 5);
        add(table);
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
        return new StringResourceStream("""
            <wicket:panel>
                <table wicket:id="table"/>
            </wicket:panel>
        """);
    }

    private class ActionPanel extends Panel {

        public ActionPanel(String id, IModel<Resource> model) {
            super(id, model);
            AjaxSubmitLink imageButton = new AjaxSubmitLink("imageButton") {
                @Override
                protected void onSubmit(AjaxRequestTarget target) {
                    Resource xx = model.getObject().removeProperties();
                    xx.getModel().remove(subject.getObject(), (Property) property.getObject(), xx);
                    target.add(table);
                }
            };
            String img = "images/minus.png";

            try {
                PackageResourceReference imgResourceRef = new PackageResourceReference(getClass(), img);
                Image image = new Image("buttonImage", imgResourceRef);
                image.add(AttributeModifier.replace("width", "25"));
                image.add(AttributeModifier.replace("height", "25"));
                imageButton.add(image);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, ex.getMessage());
            }

            add(imageButton);

        }
    }
}
