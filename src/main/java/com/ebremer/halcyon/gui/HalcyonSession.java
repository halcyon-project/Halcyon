package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.keycloak.KeycloakTokenFilter;
import com.ebremer.halcyon.lib.HalcyonSettings;
import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.halcyon.puffin.Block;
import com.ebremer.halcyon.puffin.UserSessionDataStorage;
import com.ebremer.ns.HAL;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.util.Locale;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;
import org.apache.jena.query.ParameterizedSparqlString;
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
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;

public final class HalcyonSession extends WebSession {
    private String user;
    private String mv;
    private final String uuid;
    private final String uuidurn;
    //private final String token;
    private final HalcyonPrincipal principal;

    public HalcyonSession(Request request) {
        super(request);
        HalcyonSettings s = HalcyonSettings.getSettings();
        setLocale(Locale.ENGLISH);
        ServletWebRequest req = (ServletWebRequest) request;
        HttpServletRequest servletRequest = (HttpServletRequest) request.getContainerRequest();
        HttpSession httpSession = servletRequest.getSession(true);
        httpSession.setMaxInactiveInterval(60*60*24); // 1 day for now

        KeycloakSecurityContext securityContext = (KeycloakSecurityContext) req.getContainerRequest().getAttribute(KeycloakSecurityContext.class.getName());
        if (securityContext==null) {
            uuid = UUID.randomUUID().toString();
            uuidurn = "urn:uuid:"+UUID.randomUUID().toString();
            principal = new HalcyonPrincipal(uuid, true);
        } else {
            AccessToken token2 = securityContext.getToken();
            uuid = token2.getSubject();
            uuidurn = "urn:uuid:"+token2.getSubject();
            principal = new HalcyonPrincipal(new JwtToken(securityContext.getTokenString()),false);
            //principal = new HalcyonPrincipal(uuid, false);
        }
        System.out.println("    Creating session --> "+uuid);
        UserSessionDataStorage.getInstance().put(uuid, new Block());
        if (securityContext!=null) {
            ResteasyClientBuilder builder =  (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.disableTrustManager();
            ResteasyClient client = builder.build();
            client.register(KeycloakTokenFilter.class);        
            String cmd = s.getProxyHostName()+"/auth/admin/realms/"+HalcyonSettings.realm+"/users";
            ResteasyWebTarget target = client.target(cmd);
            Invocation.Builder zam = target.request();
            Response r = zam.get();
            Model da = ModelFactory.createDefaultModel();
            if (r.getStatus()==200) {
                String json = r.readEntity(String.class);
                da.add(ParseUsers(json));    
            } else {
                System.out.println("not able to update/Parse users...");
            }
            r = client.target(s.getProxyHostName()+"/auth/admin/realms/"+HalcyonSettings.realm+"/groups").request().get();
            if (r.getStatus()==200) {
                String json = r.readEntity(String.class);
                da.add(ParseGroups(json));
                ParameterizedSparqlString pss = new ParameterizedSparqlString("select distinct ?s where {?s a so:Organization}");
                pss.setNsPrefix("so", SchemaDO.NS);
                ResultSet rs = QueryExecutionFactory.create(pss.toString(),da).execSelect();
                rs.forEachRemaining(qs ->{
                    Resource gg = qs.getResource("s");
                    String url = s.getProxyHostName()+"/auth/admin/realms/"+HalcyonSettings.realm+"/groups/"+gg.getURI().substring(9)+"/members";
                    Response rr = client.target(url).request().get();
                    if (rr.getStatus()==200) {
                        String json2 = rr.readEntity(String.class);
                        JsonReader jr = Json.createReader(new StringReader(json2));
                        JsonArray ja = jr.readArray();
                        ja.forEach(p->{
                            Resource pp = da.createResource("urn:uuid:"+p.asJsonObject().getString("id"));
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
                    System.out.println("DataCore online....\nUpdating Groups and Users...");
                    DataCore.getInstance().replaceNamedGraph(HAL.GroupsAndUsers, da);
                } else {
                    System.out.println("DataCore NOT online....");
                }
            } else {
                System.out.println("not able to update/Parse groups...");
            }
        }
    }
    
    public String getUUIDURN() {
        return uuidurn;
    }
    
    public String getUUID() {
        return uuid;
    }
    
    public Block getBlock() {
        return UserSessionDataStorage.getInstance().get(uuid);
    }
    
    @Override
    public void onInvalidate() {
        super.onInvalidate();
        System.out.println("Invalidating session --> "+uuid);
        UserSessionDataStorage.getInstance().remove(uuid);
    }
    
    public Model ParseLab(JsonObject jo) {
        Model m = ModelFactory.createDefaultModel();
        String uuidx = jo.getString("id");
        Resource s = m.createResource("urn:uuid:"+uuidx);
        m.add(m.createLiteralStatement(s, SchemaDO.name, jo.getString("name")));
        m.add(m.createLiteralStatement(s, SchemaDO.url, jo.getString("path")));
        m.add(s, RDF.type, SchemaDO.Organization);
        JsonArray ja = jo.getJsonArray("subGroups");
        for (int i=0; i<ja.size();i++) {
            JsonObject joo = ja.getJsonObject(i);
            Resource ss = m.createResource("urn:uuid:"+joo.getString("id"));
            m.add(s, SchemaDO.hasPart, ss);
            m.add(ParseLab(joo));
        }
        return m;
    }

    public Model ParseUser(JsonObject jo) {
        Model m = ModelFactory.createDefaultModel();
        String uuidx = jo.getString("id");
        Resource s = m.createResource("urn:uuid:"+uuidx);
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
        if (jo.containsKey("attributes")) {
            JsonObject attributes = jo.getJsonObject("attributes");
            if (attributes.containsKey("webid")) {
                m.add(m.createLiteralStatement(s, HAL.webid, m.createResource(attributes.getJsonArray("webid").getString(0))));
            }
        }
        return m;
    }
    
    public Model ParseUsers(String json) {
        JsonReader jr = Json.createReader(new StringReader(json));
        JsonArray ja = jr.readArray();
        Model m = ModelFactory.createDefaultModel(); 
        for (int i=0; i<ja.size(); i++) {
            m.add(ParseUser(ja.getJsonObject(i)));
        }
        return m;
    }

    public Model ParseGroups(String json) {
        JsonArray ja = Json.createReader(new StringReader(json)).readArray();
        Model m = ModelFactory.createDefaultModel();
        for (int i=0; i<ja.size(); i++) {
            m.add(ParseLab(ja.getJsonObject(i)));
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
