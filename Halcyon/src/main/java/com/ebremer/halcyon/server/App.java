package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.keycloak.HalcyonApplianceBootstrap;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.util.NoSuchElementException;
import org.keycloak.Config;
import org.keycloak.exportimport.ExportImportManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.managers.ApplianceBootstrap;
import org.keycloak.services.resources.KeycloakApplication;
import org.keycloak.services.util.JsonConfigProviderFactory;
import com.ebremer.halcyon.server.keycloak.providers.JsonProviderFactory;
import java.io.File;
import java.io.IOException;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.Path;
import org.keycloak.exportimport.ExportImportConfig;
import org.keycloak.models.KeycloakSessionFactory;
import static org.keycloak.services.resources.KeycloakApplication.getSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Path("/")
@ApplicationPath("/")
public class App extends KeycloakApplication {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
     
    @Override
    protected void startup() {
        super.startup();
        System.out.println("Starting Keycloak...");
    }
    
    @Override
    protected void loadConfig() {
	JsonConfigProviderFactory factory = new JsonProviderFactory();
	Config.init(factory.create().orElseThrow(() -> new NoSuchElementException("No value present")));
    }

    @Override
    protected ExportImportManager bootstrap() {
	final ExportImportManager exportImportManager = super.bootstrap();
	createMasterRealmAdminUser();
        tryImportRealm();
        createRealmUser("admin","admin");
	return exportImportManager;
    }

    private void createMasterRealmAdminUser() {
	try (KeycloakSession session = getSessionFactory().create()) {
            ApplianceBootstrap applianceBootstrap = new ApplianceBootstrap(session);
            try {
                session.getTransactionManager().begin();
                applianceBootstrap.createMasterRealmUser(KeycloakProperties.getInstance().getusername(), KeycloakProperties.getInstance().getpassword());
                session.getTransactionManager().commit();
            } catch (Exception ex) {
                logger.warn("Couldn't create keycloak master admin user: {}", ex.getMessage());
                session.getTransactionManager().rollback();
            }
	}
    }
    
    private void createRealmUser(String username, String password) {
	try (KeycloakSession session = getSessionFactory().create()) {
            HalcyonApplianceBootstrap applianceBootstrap = new HalcyonApplianceBootstrap(session);
            try {
                session.getTransactionManager().begin();
                applianceBootstrap.createRealmUser(username, password);
                session.getTransactionManager().commit();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                logger.warn("Couldn't create keycloak "+HalcyonSettings.realm+" "+username+" user: {}", ex.getMessage());
                session.getTransactionManager().rollback();
            }
	}
    }
    
    private void tryImportRealm() {
        Resource importLocation = new FileSystemResource("keycloak-realm-config.json");
        if (!importLocation.exists()) {
            logger.info("Could not find keycloak import file {}", importLocation);
            return;
        }
        File file;
        try {
            file = importLocation.getFile();
        } catch (IOException e) {
            logger.error("Could not read keycloak import file {}", importLocation, e);
            return;
        }
        logger.info("Starting Keycloak realm configuration import from location: {}", importLocation);
        try (KeycloakSession session = getSessionFactory().create()) {
            ExportImportConfig.setAction("import");
            ExportImportConfig.setProvider("singleFile");
            ExportImportConfig.setFile(file.getAbsolutePath());
            ExportImportManager manager = new ExportImportManager(session);
            manager.runImport();
        }
        logger.info("Keycloak realm configuration import finished.");
    }
}
