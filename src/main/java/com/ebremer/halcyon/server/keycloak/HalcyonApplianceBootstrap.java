package com.ebremer.halcyon.server.keycloak;

import com.ebremer.halcyon.HalcyonSettings;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.ApplianceBootstrap;

/**
 *
 * @author erich
 */
public class HalcyonApplianceBootstrap extends ApplianceBootstrap {
    private final KeycloakSession session;
    
    public HalcyonApplianceBootstrap(KeycloakSession session) {
        super(session);
        this.session = session;
    }
    
    public void createRealmUser(String username, String password) {
        RealmModel realm = session.realms().getRealmByName(HalcyonSettings.realm);
        session.getContext().setRealm(realm);
        if (session.users().getUsersCount(realm) > 0) {
            throw new IllegalStateException("Can't create initial user as users already exists");
        }
        UserModel user = session.users().addUser(realm, username);
        user.setEnabled(true);
        UserCredentialModel usrCredModel = UserCredentialModel.password(password);
        user.credentialManager().updateCredential(usrCredModel);
        session.groups().getTopLevelGroupsStream(realm).forEach(yay->{
            System.out.println(yay.getName()+" "+yay.getId());
            if ("admin".equals(yay.getName())) {
                user.joinGroup(yay);
            }
        });
        
        
        
        
        //GroupModel group = realm.
        //user.joinGroup(group);
        //RoleModel adminRole = realm.getRole(AdminRoles.MANAGE_REALM);
        //user.grantRole(adminRole);
    }
    
}
