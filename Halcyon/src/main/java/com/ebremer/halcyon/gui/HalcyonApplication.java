package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.sparql.Sparql;
import com.ebremer.halcyon.wicket.ListImages;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import com.ebremer.halcyon.puffin.Puffin;
import com.ebremer.halcyon.puffin.ResourceConverter;
import com.ebremer.halcyon.wicket.AccountPage;
import com.ebremer.halcyon.wicket.AdminPage;
import com.ebremer.halcyon.wicket.ethereal.Graph3D;
import com.ebremer.halcyon.wicket.ethereal.Zephyr;
import com.ebremer.multiviewer.MultiViewer;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.ConverterLocator;
import org.apache.wicket.IConverterLocator;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;

public class HalcyonApplication extends WebApplication {
    private static final HalcyonSettings settings = HalcyonSettings.getSettings();
    private final DataCore datacore;
    private final SPARQLEndPoint sep;
    
    public HalcyonApplication() {
        System.out.println("Starting Halcyon UI...");
    //    try {
      //      Thread.sleep(20000);
        //} catch (InterruptedException ex) {
          //  Logger.getLogger(HalcyonApplication.class.getName()).log(Level.SEVERE, null, ex);
       // }
        datacore = DataCore.getInstance();
        sep = SPARQLEndPoint.getSPARQLEndPoint();
    }
    
    public HalcyonSettings getSettings() {
        return settings;
    }

    public DataCore getDataCore() {
        return datacore;
    }    

    @Override
    public Class<? extends WebPage> getHomePage() {
	return HomePage.class;
    }
    
    @Override
    public Session newSession(Request request, Response response) {
        return new HalcyonSession(request);
    }
    
    @Override
    protected IConverterLocator newConverterLocator() {
        ConverterLocator converterLocator = new ConverterLocator();
        converterLocator.set(Resource.class, new ResourceConverter());        
        return converterLocator;
    }

    @Override
    public void init() {
	super.init();
        FileReaderFactoryProvider.contains("yay");           
        this.getRequestLoggerSettings().setRequestLoggerEnabled(true);
        this.getRequestLoggerSettings().setRecordSessionSize(true);
        getCspSettings().blocking().disabled();
        getApplicationSettings().setUploadProgressUpdatesEnabled(true);
        getResourceSettings().setThrowExceptionOnMissingResource(false);
        getDebugSettings().setAjaxDebugModeEnabled(true);
        mountPage("/", HomePage.class);
        mountPage("/admin", AdminPage.class);
        mountPage("/user/account", AccountPage.class);
        mountPage("/user/colorclasses", ColorClasses.class); 
        mountPage("/login", Login.class);
        mountPage("/ListImages", ListImages.class);
        mountPage("/viewer", MultiViewer.class); 
        mountPage("/collections", Collections.class);
        mountPage("/sparql", Sparql.class);
        mountPage("/about", About.class);
        mountPage("/threed", Graph3D.class);
        mountPage("/revisionhistory", RevisionHistory.class);
        mountPage("/zephyrx", Zephyr.class);
        mountPage("/viewall", ViewAll.class); 
        mountPage("/testviewall", TestViewAll.class); 
        mountPage("/puffin", Puffin.class); 
        
        //mountPage("/login", LogHal.class);
        //mountPage("/gui/dicom", DICOM.class);
        //mountPage("/gui/dicom2", DCM.class);
    }
        
    @Override
    public RuntimeConfigurationType getConfigurationType() {
        return RuntimeConfigurationType.DEVELOPMENT;
        //return RuntimeConfigurationType.DEPLOYMENT;
    }
}

/*
        mountPage("/", HomePage.class);
        mountPage("/gui/adminme", AdminPage.class);
        mountPage("/gui/accountpage", AccountPage.class);
        mountPage("/gui/login", Login.class);
        mountPage("/gui/yay", BasePage.class);
        mountPage("/gui/ListImages", ListImages.class);
       // mountPage("/gui/ListFeatures", ListFeatures.class);
        mountPage("/gui/viewer", MultiViewer.class); 
        mountPage("/gui/collections", Collections.class);
        mountPage("/gui/sparql", Sparql.class);
        mountPage("/gui/about", About.class);
        mountPage("/gui/threed", Graph3D.class);
        mountPage("/gui/revisionhistory", RevisionHistory.class);
        mountPage("/gui/zephyr", Zephyr.class);
        mountPage("/gui/login", LogHal.class);
        //mountPage("/gui/dicom", DICOM.class);
        //mountPage("/gui/dicom2", DCM.class);
*/