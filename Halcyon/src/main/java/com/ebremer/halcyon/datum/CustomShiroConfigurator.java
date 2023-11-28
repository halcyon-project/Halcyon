package com.ebremer.halcyon.datum;

import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.config.Ini;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.text.IniRealm;

public class CustomShiroConfigurator {

    private final SecurityManager securityManager;

    public CustomShiroConfigurator(Ini ini) {
        try {
            IniRealm iniRealm = new IniRealm(ini);
            securityManager = createDefaultSecurityManager(iniRealm);
        } catch (ConfigurationException e) {
            throw new RuntimeException("Failed to configure Shiro", e);
        }
    }

    private SecurityManager createDefaultSecurityManager(IniRealm iniRealm) {
        DefaultSecurityManager dsm = new DefaultSecurityManager();
        dsm.setRealm(iniRealm);
        return securityManager;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }
}
