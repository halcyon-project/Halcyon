package com.ebremer.halcyon.fuseki;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.data.DataCore;
//import com.ebremer.halcyon.datum.SessionsManager;
//import com.ebremer.halcyon.keycloak.HALKeycloakOIDCFilter;
//import com.ebremer.halcyon.keycloak.KeycloakOIDCFilterConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import javax.servlet.DispatcherType;
import org.apache.jena.fuseki.main.FusekiServer;
//import org.keycloak.adapters.servlet.KeycloakOIDCFilter;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionIdManager;
import org.eclipse.jetty.server.session.DefaultSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;

/**
 *
 * @author erich
 */
public class SPARQLEndPoint {
    private static SPARQLEndPoint sep = null;
    private static FusekiServer server = null;
    
    private SPARQLEndPoint() {       
        System.out.println("Starting Fuseki...");
        //KeycloakOIDCFilterConfig config = new KeycloakOIDCFilterConfig("keycloak");
        //config.setInitParameter(KeycloakOIDCFilter.CONFIG_FILE_PARAM, "keycloak.json");
        //HALKeycloakOIDCFilter filter = new HALKeycloakOIDCFilter();
        //filter.setSessionIdMapper(SessionsManager.getSessionsManager().getSessionIdMapper());
        //filter.setConfig(config);
        server = FusekiServer.create()
            //.add("/rdf", DataCore.getInstance().getSecuredDataset())
            .add("/rdf", DataCore.getInstance().getDataset())
                
                
           // .loopback(true)
          //  .securityHandler(new HalcyonSecurityHandler())
            
            .enableCors(true)
            .port(HalcyonSettings.getSettings().GetSPARQLPort())
           // .addFilter("/*", filter)
            .build();
        //config.setContext(server.getServletContext());
        //SessionHandler sessionHandler = new SessionHandler();
        //DefaultSessionCache sessionCache = new DefaultSessionCache(sessionHandler);
        //DefaultSessionIdManager sim = new DefaultSessionIdManager(server.getJettyServer());
        //server.getJettyServer().setSessionIdManager(sim);
        
        Server jettyServer = server.getJettyServer();
        ServletContextHandler servletContextHandler = (ServletContextHandler) jettyServer.getHandler();
        
        ServletHandler servletHandler = servletContextHandler.getServletHandler();
        
        EnvironmentLoaderListener ell = new EnvironmentLoaderListener();
        servletContextHandler.addEventListener(ell);
        
        List<FilterMapping> mappings = new ArrayList<>(Arrays.asList(servletHandler.getFilterMappings()));
        List<FilterHolder> holders = new ArrayList<>(Arrays.asList(servletHandler.getFilters()));
        {
            FilterHolder holder1 = new FilterHolder();
            holder1.setName("halcyonfusekifilter");
            
            //FilterHolder keycloakholder1 = new FilterHolder();
            //keycloakholder1.setName("halcyonkeycloakfilter");
            
            holder1.setFilter(new ShiroFilter());
            //keycloakholder1.setFilter(filter);
            
            FilterMapping mapping1 = new FilterMapping();
            mapping1.setFilterName(holder1.getName());
            mapping1.setPathSpec("/*");
            mapping1.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));

            //FilterMapping keycloakmapping1 = new FilterMapping();
            //keycloakmapping1.setFilterName(keycloakholder1.getName());
            //keycloakmapping1.setPathSpec("/rdf");
            //keycloakmapping1.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
            
            
            mappings.add(0, mapping1);
            holders.add(0, holder1);
            
            //mappings.add(0, keycloakmapping1);
            //holders.add(0, keycloakholder1);
        }
        
        FilterMapping[] mappings3 = new FilterMapping[mappings.size()];
        mappings3 = mappings.toArray(mappings3);
        FilterHolder[] holders3 = new FilterHolder[holders.size()];
        holders3 = holders.toArray(holders3);
        servletHandler.setFilters(holders3);
        servletHandler.setFilterMappings(mappings3);
        
        SessionIdManager idmanager = new DefaultSessionIdManager(jettyServer);
        jettyServer.setSessionIdManager(idmanager);
        
        SessionHandler sessionsHandler = new SessionHandler();
        //sessionsHandler.setUsingCookies(false);
        servletHandler.setHandler(sessionsHandler);
        sessionsHandler.getSessionCookieConfig().setName("JETTYFUSEKISESSION");
                
        server.start();
    }
    
    public static FusekiServer getFusekiServer() {
        return server;
    }
    
    /*
    
    private static void configureShiro() {
        String iniConfig =
            """
            [users]
            admin=admin,administrator
            user=user,user
            [roles]
            administrator=admin:*,read:*,write:*,execute:*,create:*,delete:*
            user=read:*,write:*,execute:*
            """;
        InputStream input = new ByteArrayInputStream(iniConfig.getBytes());
        Ini ini = new Ini();
        ini.load(input);
        CustomShiroConfigurator shiroConfigurator = new CustomShiroConfigurator(ini);
        SecurityManager securityManager = shiroConfigurator.getSecurityManager();
        SecurityUtils.setSecurityManager(securityManager);
    }*/
    
    public static SPARQLEndPoint getSPARQLEndPoint() {
        if (sep==null) {
            sep = new SPARQLEndPoint();
        }
        return sep;
    }
    
    public void shutdown() {
        if (server!=null) {
            server.stop();
        }
    }
}
