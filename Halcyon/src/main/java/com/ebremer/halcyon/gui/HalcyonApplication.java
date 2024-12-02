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
import com.ebremer.halcyon.wicket.Upload;
import com.ebremer.halcyon.wicket.ethereal.Graph3D;
import com.ebremer.halcyon.wicket.ethereal.Zephyr;
import com.ebremer.halcyon.wicket.ethereal.Zephyr2;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HalcyonApplication extends WebApplication {
    private final DataCore datacore;
    private final SPARQLEndPoint sep;
    private static final Logger logger = LoggerFactory.getLogger(HalcyonApplication.class);
    
    public HalcyonApplication() {
        System.out.println("Starting Halcyon UI...");
        datacore = DataCore.getInstance();
        sep = SPARQLEndPoint.getSPARQLEndPoint();
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
        return new HalcyonSession(request,response);
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
        //FileReaderFactoryProvider.contains("yay");           
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
        mountPage("/containers", Collections.class);
        mountPage("/upload", Upload.class);
        mountPage("/sparql", Sparql.class);
        mountPage("/about", About.class);
        mountPage("/threed", Graph3D.class);
        mountPage("/revisionhistory", RevisionHistory.class);
        mountPage("/viewall", ViewAll.class); 
        mountPage("/testviewall", TestViewAll.class); 
        mountPage("/puffin", Puffin.class); 
        mountPage("/blank", Blank.class); 

        mountPage("/zephyrx", Zephyr.class);
        mountPage("/zephyrx2", Zephyr2.class);
        
        //mountPage("/login", LogHal.class);
        //mountPage("/gui/dicom", DICOM.class);
        //mountPage("/gui/dicom2", DCM.class);
    }
        
    @Override
    public RuntimeConfigurationType getConfigurationType() {
        if (HalcyonSettings.getSettings().isDevMode()) {
            return RuntimeConfigurationType.DEVELOPMENT;
        }
        return RuntimeConfigurationType.DEPLOYMENT;
    }
}
