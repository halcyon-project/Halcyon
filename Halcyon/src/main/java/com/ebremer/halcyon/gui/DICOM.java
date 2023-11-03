package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.wicket.dwv.DWVPanel;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.IMarkupCacheKeyProvider;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

public class DICOM extends WebPage implements IMarkupResourceStreamProvider, IMarkupCacheKeyProvider {

        private static final long serialVersionUID = 1L;

        public DICOM() {
            System.out.println("DICOM View");
            final Label l = new Label("msg", "Halcyon DICOM Integration");
            add(l);
            DWVPanel dwv = new DWVPanel("viewer1", "GRAH!");
            dwv.setVisible(true);
            add(dwv);
        }
        
    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
        //return new StringResourceStream("<html><title>This is Halcyon!</title><body><div wicket:enclosure='msg'><span wicket:id='msg'></span></div><input type='button' value='Toggle' wicket:id='b'/><a wicket:id = \"login\">Click this BookmarkablePageLink to go to Page1</a></body></html>\n");
        return new StringResourceStream("<html><title>This is Halcyon!</title><body><span wicket:id='msg'></span><div wicket:id=viewer1></div></body></html>");
        }
   
    @Override
    public String getCacheKey(MarkupContainer container, Class<?> containerClass) {
        final String classname = containerClass.isAnonymousClass() ? containerClass.getSuperclass().getSimpleName() : containerClass.getSimpleName();
        return classname;
        //return classname + '_' + lClass + '_' + rClass + '_' + (isSimple() ? 1 : 0) + '_';
    }
}
