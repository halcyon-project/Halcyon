package com.ebremer.multiviewer;

import java.util.LinkedHashMap;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.request.resource.ResourceReference;

public class MultiViewerFoundation {
    private static MultiViewerFoundation mvf = null;
    private static final LinkedHashMap<CSS,ResourceReference> css = new LinkedHashMap<>();
    private static final LinkedHashMap<JS,ResourceReference> js  = new LinkedHashMap<>();
    public static enum CSS { main, colorpicker, fontawesome }
    public static enum JS {
        openseadragon,
        openseadragonfabricjsoverlay,
        jquery,
        fabricadapted,
        openseadragonfiltering,
        openseadragonscalebar,
        colorpicker,
        commonFunctions,
        pageSetup,
        editPolygon,
        drawPolygon,
        gridOverlay,      
        mapMarker,
        ruler,
        blender,
        markupTools,
        draggable,
        filterPopup,
        filters,
        ImageViewer,
        layerUI,
        layerPopup,
        MultiViewer,
        synchronizeViewers
    }
    
    public MultiViewerFoundation() {
        AddCSSResource(CSS.main,"css/main.min.css");
        AddCSSResource(CSS.colorpicker,"vendor/color-picker/index.min.css");
        AddCSSResource(CSS.fontawesome,"vendor/fontawesome-free-6.5.1-web/css/all.min.css");

        AddJSResource(JS.openseadragon,"vendor/openseadragon/openseadragon.min.js");
        AddJSResource(JS.openseadragonfabricjsoverlay,"vendor/OpenseadragonFabricjsOverlay/osd-fabricjs-overlay.min.js");
        AddJSResource(JS.jquery,"vendor/jquery/jquery.min.js");
        AddJSResource(JS.fabricadapted,"vendor/fabricjs/fabric.min.js");
        AddJSResource(JS.openseadragonfiltering,"vendor/OpenSeadragonFiltering/osd-filtering.min.js");
        AddJSResource(JS.openseadragonscalebar,"vendor/OpenSeadragonScalebar/osd-scalebar.min.js");
        AddJSResource(JS.colorpicker,"vendor/color-picker/index.min.js");
        
        AddJSResource(JS.commonFunctions,"src/commonFunctions.js");        
        AddJSResource(JS.pageSetup,"src/pageSetup.js");
        AddJSResource(JS.editPolygon,"src/editPolygon.js");
        AddJSResource(JS.drawPolygon,"src/drawPolygon.js");
        AddJSResource(JS.gridOverlay,"src/gridOverlay.js");
        AddJSResource(JS.mapMarker,"src/mapMarker.js");
        AddJSResource(JS.ruler,"src/ruler.js");
        AddJSResource(JS.blender,"src/blender.js");
        AddJSResource(JS.markupTools,"src/markupTools.js");
        AddJSResource(JS.draggable,"src/draggable.js");
        AddJSResource(JS.filterPopup,"src/filterPopup.js");
        AddJSResource(JS.filters,"src/filters.js");
        AddJSResource(JS.ImageViewer,"src/ImageViewer.js");
        AddJSResource(JS.layerUI,"src/layerUI.js");
        AddJSResource(JS.layerPopup,"src/layerPopup.js");
        AddJSResource(JS.synchronizeViewers,"src/synchronizeViewers.js");
    }
    
    public final void AddCSSResource(CSS name, String bundle) {
        css.put(name, new CssResourceReference(MultiViewerFoundation.class, bundle));
    }
    
    public final void AddJSResource(JS name, String bundle) {
        js.put(name, new JavaScriptResourceReference(MultiViewerFoundation.class, bundle));
    }    
    
    public static JavaScriptResourceReference getJS(JS name) {
        if (mvf==null) {
            mvf = new MultiViewerFoundation();
        }
        JavaScriptResourceReference res = (JavaScriptResourceReference )js.get(name);
        return res;
    }
    
    public static CssResourceReference getJS(CSS name) {
        if (mvf==null) {
            mvf = new MultiViewerFoundation();
        }
        CssResourceReference res = (CssResourceReference)css.get(name);
        return res;
    }
    
    public static void AddJSCSS(IHeaderResponse response) {
        if (mvf==null) {mvf = new MultiViewerFoundation();}
        css.forEach((k, v) -> {
            System.out.println(v);
            response.render(CssHeaderItem.forReference((CssResourceReference) v));
        });
        js.forEach((k, v) -> {
            System.out.println(v);
            response.render(JavaScriptHeaderItem.forReference((JavaScriptResourceReference) v));
        });        
    }
}
