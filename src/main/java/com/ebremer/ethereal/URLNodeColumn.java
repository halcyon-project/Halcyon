package com.ebremer.ethereal;

import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class URLNodeColumn<T, S> extends NodeColumn<T, S> {

    public URLNodeColumn(IModel<String> displayModel, S sortProperty, String propertyExpression) {
        super(displayModel, sortProperty, propertyExpression);
    }
    
    @Override
    public void populateItem(final Item<ICellPopulator<T>> item, final String componentId, final IModel<T> rowModel) {
      //  item.add(new ExternalLink(componentId, getDataModel(rowModel)));
    }
}