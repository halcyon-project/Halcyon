package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.sparql.Sparql;
import com.ebremer.halcyon.wicket.ListImages;
import com.ebremer.halcyon.wicket.Stacks;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import com.ebremer.halcyon.wicket.AccountPage;
import com.ebremer.halcyon.wicket.AdminPage;
import com.ebremer.halcyon.wicket.Upload;
import com.ebremer.halcyon.wicket.ethereal.Graph3D;
import com.ebremer.multiviewer.MultiViewer;
import com.ebremer.vandegraph.VandegraphApplication;
import org.apache.jena.query.Dataset;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HalcyonApplication extends VandegraphApplication {
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
    protected Dataset createDataset() {
        // The vandegraph form layers (CommandNode, SelectDataProvider, ...)
        // read/write whatever this returns — Halcyon's data lives in DataCore.
        return datacore.getDataset();
    }

    @Override
    protected boolean ownsDataset() {
        // DataCore manages the dataset lifecycle; don't close it on app destroy.
        return false;
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
    public void init() {
	super.init();
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
        mountPage("/stacks", Stacks.class);
        mountPage("/viewer", MultiViewer.class);
        mountPage("/containers", Collections.class);
        // The W3C Linked Web Storage browser. Note the path: it must NOT begin with
        // "W3Clws", because Wicket's ignore list is a raw prefix match, so such a page
        // would be excluded from Wicket by the very entries that let the LWS servlets
        // through.
        mountPage("/storage", com.ebremer.halcyon.lws.StoragePage.class);
        mountPage("/upload", Upload.class);
        mountPage("/sparql", Sparql.class);
        mountPage("/about", About.class);
        mountPage("/threed", Graph3D.class);
        mountPage("/revisionhistory", RevisionHistory.class);
        mountPage("/viewall", ViewAll.class);
        mountPage("/testviewall", TestViewAll.class);
        mountPage("/blank", Blank.class);
    }
        
    @Override
    public RuntimeConfigurationType getConfigurationType() {
        if (HalcyonSettings.getSettings().isDevMode()) {
            return RuntimeConfigurationType.DEVELOPMENT;
        }
        return RuntimeConfigurationType.DEPLOYMENT;
    }
}
