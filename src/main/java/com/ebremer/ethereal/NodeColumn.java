package com.ebremer.ethereal;

import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.IExportableColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;

public class NodeColumn<T, S> extends AbstractColumn<T, S> implements IExportableColumn<T, S> {
    private static final long serialVersionUID = 1L;
    private final String predicateExpression;

    public NodeColumn(final IModel<String> displayModel, final S sortProperty, final String propertyExpression) {
        super(displayModel, sortProperty);
        this.predicateExpression = propertyExpression;
    }

    public NodeColumn(final IModel<String> displayModel, final String propertyExpression) {
        super(displayModel, null);
        this.predicateExpression = propertyExpression;
    }

    @Override
    public void populateItem(final Item<ICellPopulator<T>> item, final String componentId, final IModel<T> rowModel) {
        //System.out.println(rowModel.getObject().getClass().toString()+"   populateItem "+predicateExpression);
        if ("xcreator".equals(predicateExpression)) {
            String ss = "BLANK";
            item.add(new ExternalLink(componentId,ss,ss));
        } else {
            Solution s = (Solution) ((SolutionModel) rowModel).getInnermostModelOrObject();
            item.add(new Label(componentId, s.get(predicateExpression)));
       }           
    }

    public String getPropertyExpression() {
	return predicateExpression;
    }

    @Override
    public IModel<?> getDataModel(IModel<T> rowModel) {
        //System.out.println("getDataModel : "+rowModel.getObject().getClass().toString());
        SolutionModel sm = new SolutionModel(rowModel);
        //System.out.println("INNER : "+sm.getInnermostModelOrObject().getClass().toString());
        return sm;
    }
}