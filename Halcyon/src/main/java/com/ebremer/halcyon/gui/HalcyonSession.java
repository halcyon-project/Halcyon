package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.vandegraph.VandegraphSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.WebIdLogin;
import com.ebremer.lws.auth.oidc.WebIdOidcLogin;
import com.ebremer.ns.HAL;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.Locale;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Optional;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.http.WebResponse;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.oidc.profile.OidcProfile;

public final class HalcyonSession extends VandegraphSession {
    private static final Logger logger = LoggerFactory.getLogger(HalcyonSession.class);
    private String user;
    private String mv;
    private final String userURI;
    private final HalcyonPrincipal principal;

    public HalcyonSession(Request request, org.apache.wicket.request.Response response) {
        super(request);
        logger.debug("Creating session");
        ServletWebRequest req = (ServletWebRequest) request;
        HttpServletRequest servletRequest = (HttpServletRequest) req.getContainerRequest();        
        WebResponse webResponse = (WebResponse) response;
        HttpServletResponse httpServletResponse = (HttpServletResponse) webResponse.getContainerResponse();
        JEEContext context = new JEEContext(servletRequest, httpServletResponse);
        ProfileManager profileManager = new ProfileManager(context, new JEESessionStore());
        Optional<UserProfile> profile = profileManager.getProfile();
        HalcyonSettings s = HalcyonSettings.getSettings();
        setLocale(Locale.ENGLISH);
        HttpSession httpSession = servletRequest.getSession(true);
        httpSession.setMaxInactiveInterval(60*60*24); // 1 day for now
        // Option B: an interactive WebID login (the /webid-callback servlet) seats its result as a
        // session attribute. Identity is the WebID itself; this path carries no Keycloak token and
        // therefore skips the Keycloak-admin group sync below.
        String webidLogin = (String) httpSession.getAttribute(WebIdLogin.WEBID);
        if (webidLogin != null && !webidLogin.isBlank()) {
            userURI = webidLogin;
            // Groups come from the local WebID->role map (WebIdLogin.groupsFor), never the OP's token.
            // The retained tokens are the credential the GUI presents to LWS storage as this WebID
            // (the principal refreshes the ID Token from them on demand).
            WebIdOidcLogin.Tokens webidTokens =
                    (WebIdOidcLogin.Tokens) httpSession.getAttribute(WebIdLogin.TOKENS);
            principal = new HalcyonPrincipal(webidLogin, WebIdLogin.groupsFor(webidLogin),
                    webidTokens, WebIdLogin.allowedHosts());
            // getUser() is the short, path-safe username (a WebID's last segment), exactly like the
            // Keycloak branch's preferred_username below — NOT the WebID itself, which getUserURI()
            // carries. A per-user storage path built from the raw WebID has %2F-laden segments the
            // storage rejects (HTTP 400); the /colorclasses palette relay already keys on the short
            // name, so the editor must resolve the SAME document path.
            user = principal.getPreferredUserName();
        } else if (profile.isPresent()) {
            OidcProfile oidcProfile = (OidcProfile) profile.get();
            String jwt = oidcProfile.getAccessToken().getValue();
            JwtToken haha = new JwtToken(jwt);
            user = haha.getPrincipal().getPreferredUserName();
            userURI = haha.getPrincipal().getUserURI();
            principal = new HalcyonPrincipal(haha,false);
        } else {
            logger.debug("No pac4j profile present; anonymous session");
            userURI = "urn:uuid:"+UUID.randomUUID().toString();
            principal = new HalcyonPrincipal(userURI, true);
        }
        // The Keycloak admin REST calls below (users, groups, group members) only mean
        // anything when that subsystem is running. With :AuthServer commented out there is
        // no pac4j profile to be had either, so this is belt and braces — but it states the
        // dependency instead of leaving it to be inferred from the profile being empty.
        if (webidLogin == null && profile.isPresent() && s.isKeycloakEnabled()) {
            OidcProfile oidcProfile = (OidcProfile) profile.get();
            String jwt = oidcProfile.getAccessToken().getValue();
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.disableTrustManager();
            ResteasyClient client = builder.build();
            String cmd = s.getProxyHostName()+"/auth/admin/realms/"+HalcyonSettings.REALM+"/users";
            ResteasyWebTarget target = client.target(cmd);
            logger.debug("Keycloak admin request: {}", cmd);
            Invocation.Builder zam = target.request();
            zam.header("Authorization", "Bearer "+jwt);
            Response r = zam.get();
            Model da = ModelFactory.createDefaultModel();
            if (r.getStatus()==200) {
                String json = r.readEntity(String.class);
                da.add(ParseUsers(json));    
            } else {
                logger.warn("Unable to update/parse users from Keycloak (HTTP {})", r.getStatus());
            }            
            cmd = s.getAuthServer()+"/admin/realms/"+HalcyonSettings.REALM+"/groups";
            target = client.target(cmd);
            logger.debug("Keycloak admin request: {}", cmd);
            zam = target.request();
            zam.header("Authorization", "Bearer "+jwt);
            r = zam.get();           
            if (r.getStatus()==200) {
                String json = r.readEntity(String.class);
                HashMap<String,String> map = new HashMap<>();
                da.add(ParseGroups(json, map));
                ParameterizedSparqlString pss = new ParameterizedSparqlString("select distinct ?s where {?s a so:Organization}");
                pss.setNsPrefix("so", SchemaDO.NS);
                // H13: close the execution. The loop below does HTTP calls per row, so
                // materialise first and let the QueryExecution go immediately rather
                // than holding it open across the network I/O.
                ResultSet rs;
                try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), da)) {
                    rs = qe.execSelect().materialise();
                }
                rs.forEachRemaining(qs ->{
                    Resource gg = qs.getResource("s");                    
                    String cmdx = s.getAuthServer()+"/admin/realms/"+HalcyonSettings.REALM+"/groups/"+map.get(gg.getURI())+"/members";
                    ResteasyWebTarget targetx = client.target(cmdx);
                    logger.debug("Keycloak admin request: {}", cmdx);
                    Invocation.Builder zamx = targetx.request();
                    zamx.header("Authorization", "Bearer "+jwt);
                    Response rr = zamx.get();
                    if (rr.getStatus()==200) {
                        String json2 = rr.readEntity(String.class);
                        JsonReader jr = Json.createReader(new StringReader(json2));
                        JsonArray ja = jr.readArray();
                        ja.forEach(p->{
                            Resource pp = da.createResource(HalcyonSettings.getSettings().getHostName()+"/user/"+p.asJsonObject().getString("username").replace(" ", "%20"));
                            da.add(gg,SchemaDO.member,pp);
                            da.add(pp,SchemaDO.memberOf,gg);
                        });
                    }
                });
                da.createResource(HAL.Anonymous.toString())
                    .addProperty(RDF.type, SchemaDO.Organization)
                    .addProperty(SchemaDO.name, "Anonymous Sessions");
                DataCore dc = DataCore.getInstance();
                if (dc.getDataset()!=null) {
                    // L1: da.write(System.out, "TURTLE") dumped the entire assembled
                    // users+groups graph — every name, email and membership in the realm —
                    // to stdout on every authenticated session creation.
                    logger.debug("DataCore online; updating groups and users");
                    DataCore.getInstance().replaceNamedGraph(HAL.GroupsAndUsers, da);
                } else {
                    logger.warn("DataCore not online; groups and users not updated");
                }
            } else {
                logger.warn("Unable to update/parse groups from Keycloak (HTTP {})", r.getStatus());
            }
        }
        logger.debug("Creating session... done");
    }
    
    public Model ParseLab(JsonObject jo, HashMap<String,String> map) {
        Model m = ModelFactory.createDefaultModel();
        String groupid = HalcyonSettings.getSettings().getHostName()+"/groups"+jo.getString("path").replace(" ", "%20");
        Resource s = m.createResource(groupid);
        m.add(m.createLiteralStatement(s, SchemaDO.name, jo.getString("name")));
        m.add(m.createLiteralStatement(s, SchemaDO.url, jo.getString("path")));
        map.put(HalcyonSettings.getSettings().getHostName()+"/groups"+jo.getString("path").replace(" ", "%20"), jo.getString("id"));
        m.add(s, RDF.type, SchemaDO.Organization);        
        if (jo.containsKey("subGroups")) {
            JsonArray ja = jo.getJsonArray("subGroups");
            for (int i=0; i<ja.size();i++) {
                JsonObject joo = ja.getJsonObject(i);
                Resource ss = m.createResource("urn:uuid:"+joo.getString("id"));
                m.add(s, SchemaDO.hasPart, ss);
                m.add(ParseLab(joo, map));
            }
        }
        return m;
    }

    public Model ParseUser(JsonObject jo) {
        Model m = ModelFactory.createDefaultModel();
        String userid = HalcyonSettings.getSettings().getHostName()+"/user/"+jo.getString("username").replace(" ", "%20");
        Resource s = m.createResource(userid);
        if (jo.containsKey("lastName")) {
            m.add(m.createLiteralStatement(s, SchemaDO.familyName, jo.getString("lastName")));
        }
        if (jo.containsKey("firstName")) {
            m.add(m.createLiteralStatement(s, SchemaDO.givenName, jo.getString("firstName")));
        }
        if (jo.containsKey("email")) {
            m.add(m.createLiteralStatement(s, SchemaDO.email,jo.getString("email")));
        }
        m.add(s, RDF.type, SchemaDO.Person);
        
        //jo.keySet().forEach(k->{
//            System.out.println(k+" ---------- "+jo.getString(k));
  //      });
        
        if (jo.containsKey("attributes")) {
            JsonObject attributes = jo.getJsonObject("attributes");
            if (attributes.containsKey("webid")) {
                m.add(m.createLiteralStatement(s, HAL.webid, m.createResource(attributes.getJsonArray("webid").getString(0))));
            }
        }
        return m;
    }
    
    // L1: the Keycloak /users payload was printed verbatim — the entire realm
    // directory (username, firstName, email, webid) to stdout on every session.
    public Model ParseUsers(String json) {
        JsonReader jr = Json.createReader(new StringReader(json));
        JsonArray ja = jr.readArray();
        Model m = ModelFactory.createDefaultModel(); 
        for (int i=0; i<ja.size(); i++) {
            m.add(ParseUser(ja.getJsonObject(i)));
        }
        return m;
    }

    // L1: as ParseUsers — this printed the whole groups payload.
    public Model ParseGroups(String json, HashMap<String,String> map) {
        JsonArray ja = Json.createReader(new StringReader(json)).readArray();
        Model m = ModelFactory.createDefaultModel();
        for (int i=0; i<ja.size(); i++) {
            m.add(ParseLab(ja.getJsonObject(i), map));
        }
        return m;
    }
    
    public static HalcyonSession get() {
        return (HalcyonSession) Session.get();
    }
    
    public HalcyonPrincipal getHalcyonPrincipal() {
        return principal;
    }

    public synchronized String getUser() {
        return user;
    }
    
    public String getUserURI() {
        return userURI;
    }

    public synchronized String getMV() {
        return mv;
    }

    public synchronized void SetMV(String mv) {
        this.mv = mv;
    }

    public synchronized boolean isAuthenticated() {
        return (user != null);
    }
    
    public synchronized void setUser(String user) { 
        this.user = user;
        dirty();
    }
}
