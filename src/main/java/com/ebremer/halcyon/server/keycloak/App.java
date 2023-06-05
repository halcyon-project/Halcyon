package com.ebremer.halcyon.server.keycloak;

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

import lombok.extern.slf4j.Slf4j;
import org.keycloak.exportimport.ExportImportConfig;
import static org.keycloak.services.resources.KeycloakApplication.getSessionFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Slf4j
public class App extends KeycloakApplication {

    static ServerProperties properties;

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
	return exportImportManager;
    }

    private void createMasterRealmAdminUser() {
	KeycloakSession session = getSessionFactory().create();
	ApplianceBootstrap applianceBootstrap = new ApplianceBootstrap(session);
	try {
            session.getTransactionManager().begin();
            applianceBootstrap.createMasterRealmUser(properties.username(), properties.password());
            session.getTransactionManager().commit();
	} catch (Exception ex) {
            log.warn("Couldn't create keycloak master admin user: {}", ex.getMessage());
            session.getTransactionManager().rollback();
	}
	session.close();
    }
    
    private void tryImportRealm() {
        Resource importLocation = new FileSystemResource("keycloak-realm-config.json");
        if (!importLocation.exists()) {
            log.info("Could not find keycloak import file {}", importLocation);
            return;
        }
        File file;
        try {
            file = importLocation.getFile();
        } catch (IOException e) {
            log.error("Could not read keycloak import file {}", importLocation, e);
            return;
        }
        log.info("Starting Keycloak realm configuration import from location: {}", importLocation);
        try (KeycloakSession session = getSessionFactory().create()) {
            ExportImportConfig.setAction("import");
            ExportImportConfig.setProvider("singleFile");
            ExportImportConfig.setFile(file.getAbsolutePath());
            ExportImportManager manager = new ExportImportManager(session);
            manager.runImport();
        }
        log.info("Keycloak realm configuration import finished.");
    }
}
