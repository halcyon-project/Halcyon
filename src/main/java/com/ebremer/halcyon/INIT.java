/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.core.io.ClassPathResource;

/**
 *
 * @author erich
 */
public class INIT {
    public static String KEYCLOAKJSONPATH = "keycloak.json";
    public static String KEYCLOAKREALMCONFIGJSONPATH = "keycloak-realm-config.json";
        
    public void dump(String src) {
        if (!(new File(src)).exists()) {
            try {
                ClassPathResource cpr = new ClassPathResource(src); 
                Files.copy(cpr.getInputStream(), Paths.get(src), StandardCopyOption.REPLACE_EXISTING);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(INIT.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(INIT.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void init() {
        if (!(new File("data").exists())) {
            if (!(new File("keycloak-realm-config.json").exists())) {
                dump("keycloak-realm-config.json");
            }
        } else {
            File spent = new File("keycloak-realm-config.json");
            if (spent.exists()) {
                spent.delete();
            }
        }
        dump("keycloak.json");
        dump("settings.ttl");
    }
    
    public static void main(String[] args) {
        INIT i = new INIT();
        i.init();
      //  System.out.println(getPath());
    }
    
}
