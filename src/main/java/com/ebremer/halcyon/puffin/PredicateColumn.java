package com.ebremer.halcyon.puffin;

import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.IExportableColumn;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 * @param <Solution>
 * @param <Node>
 */
public class PredicateColumn<Solution, Node> extends AbstractColumn<Solution, Node> implements IExportableColumn<Solution, Node> {

    public PredicateColumn(IModel<String> displayModel) {
        super(displayModel);
    }

    public PredicateColumn(IModel<String> displayModel, Node sortProperty) {
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
    public void populateItem(Item<ICellPopulator<Solution>> item, String string, IModel<Solution> imodel) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IModel<?> getDataModel(IModel<Solution> imodel) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
