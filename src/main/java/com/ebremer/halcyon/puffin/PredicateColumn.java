package com.ebremer.halcyon.puffin;

import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.IExportableColumn;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 * @param <T>
 * @param <S>
 */
public class PredicateColumn<T, S> extends AbstractColumn<T, S> implements IExportableColumn<T, S> {

    public PredicateColumn(IModel<String> displayModel) {
        super(displayModel);
    }

    public PredicateColumn(IModel<String> displayModel, S sortProperty) {
        super(displayModel, sortProperty);
    }

    @Override
    public boolean isSortable() {
        return super.isSortable();
    }

    @Override
    public int getHeaderRowspan() {
        return super.getHeaderRowspan();
    }

    @Override
    public int getHeaderColspan() {
        return super.getHeaderColspan();
    }

    @Override
    public void populateItem(Item<ICellPopulator<T>> item, String string, IModel<T> imodel) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IModel<?> getDataModel(IModel<T> imodel) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
