package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.halcyon.puffin.Block;
import com.ebremer.halcyon.puffin.UserSessionDataStorage;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
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

public final class HalcyonSession extends WebSession {
    private String user;
    private String mv;
   // private final String uuid;
    //private final String uuidurn;
    private final String userURI;
    private final HalcyonPrincipal principal;

    public HalcyonSession(Request request, org.apache.wicket.request.Response response) {
        super(request);        
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
        if (profile.isPresent()) {
            OidcProfile oidcProfile = (OidcProfile) profile.get();
            String jwt = oidcProfile.getAccessToken().getValue();
            JwtToken haha = new JwtToken(jwt);
            //uuid = haha.getPrincipal().getUserURI();
            //uuidurn = haha.getPrincipal().getURNUUID();
            userURI = haha.getPrincipal().getUserURI();
            principal = new HalcyonPrincipal(haha,false);
        } else {
            System.out.println("HalcyonSession Profile not present!!!!");
            userURI = "urn:uuid:"+UUID.randomUUID().toString();
            //uuidurn = "urn:uuid:"+uuid;
            principal = new HalcyonPrincipal(userURI, true);
        }
        UserSessionDataStorage.getInstance().put(userURI, new Block());
        if (profile.isPresent()) {
            OidcProfile oidcProfile = (OidcProfile) profile.get();
            String jwt = oidcProfile.getAccessToken().getValue();
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.disableTrustManager();
            ResteasyClient client = builder.build();
            String cmd = s.getProxyHostName()+"/auth/admin/realms/"+HalcyonSettings.realm+"/users";
            ResteasyWebTarget target = client.target(cmd);
            Invocation.Builder zam = target.request();
            zam.header("Authorization", "Bearer "+jwt);
            Response r = zam.get();
            Model da = ModelFactory.createDefaultModel();
            if (r.getStatus()==200) {
                String json = r.readEntity(String.class);
                da.add(ParseUsers(json));    
            } else {
                System.out.println("not able to update/Parse users...");
            }            
            cmd = s.getAuthServer()+"/auth/admin/realms/"+HalcyonSettings.realm+"/groups";
            target = client.target(cmd);
            zam = target.request();
            zam.header("Authorization", "Bearer "+jwt);
            r = zam.get();           
            if (r.getStatus()==200) {
                String json = r.readEntity(String.class);
                HashMap<String,String> map = new HashMap<>();
                da.add(ParseGroups(json, map));
                ParameterizedSparqlString pss = new ParameterizedSparqlString("select distinct ?s where {?s a so:Organization}");
                pss.setNsPrefix("so", SchemaDO.NS);
                ResultSet rs = QueryExecutionFactory.create(pss.toString(),da).execSelect();
                rs.forEachRemaining(qs ->{
                    Resource gg = qs.getResource("s");                    
                    //String cmdx = s.getAuthServer()+"/auth/admin/realms/"+HalcyonSettings.realm+"/groups"+gg.getURI().substring(9)+"/members";
                    System.out.println(gg.getURI());
                    map.forEach((k,v)->{ System.out.println(k+"  "+v);});
                    String cmdx = s.getAuthServer()+"/auth/admin/realms/"+HalcyonSettings.realm+"/groups/"+map.get(gg.getURI())+"/members";
                    ResteasyWebTarget targetx = client.target(cmdx);
                    Invocation.Builder zamx = targetx.request();
                    zamx.header("Authorization", "Bearer "+jwt);
                    Response rr = zamx.get();
                    System.out.println(rr.getStatus());
                    if (rr.getStatus()==200) {
                        String json2 = rr.readEntity(String.class);
                        System.out.println(json2);
                        JsonReader jr = Json.createReader(new StringReader(json2));
                        JsonArray ja = jr.readArray();
                        ja.forEach(p->{
                            //Resource pp = da.createResource("urn:uuid:"+p.asJsonObject().getString("id"));
                            Resource pp = da.createResource(HalcyonSettings.getSettings().getHostName()+"/users/"+p.asJsonObject().getString("username").replace(" ", "%20"));
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
                    da.write(System.out, "TURTLE");
                    DataCore.getInstance().replaceNamedGraph(HAL.GroupsAndUsers, da);
                } else {
                    System.out.println("DataCore NOT online....");
                }
            } else {
                System.out.println("not able to update/Parse groups...");
            }
        }
    }
    
    //public String getUUIDURN() {
      //  return uuidurn;
    //}
    
    //public String getUUID() {
      //  return uuid;
    //}
    
    public Block getBlock() {
        return UserSessionDataStorage.getInstance().get(userURI);
    }
    
    @Override
    public void onInvalidate() {
        super.onInvalidate();
        System.out.println("Invalidating session --> "+userURI);
        UserSessionDataStorage.getInstance().remove(userURI);
    }
    
    public Model ParseLab(JsonObject jo, HashMap<String,String> map) {
        Model m = ModelFactory.createDefaultModel();
        //String uuidx = jo.getString("id");
        String groupid = HalcyonSettings.getSettings().getHostName()+"/groups"+jo.getString("path").replace(" ", "%20");
        //Resource s = m.createResource("urn:uuid:"+uuidx);
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
        //String uuidx = jo.getString("id");
        //Resource s = m.createResource("urn:uuid:"+uuidx);
        String userid = HalcyonSettings.getSettings().getHostName()+"/users/"+jo.getString("username").replace(" ", "%20");
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
        if (jo.containsKey("attributes")) {
            JsonObject attributes = jo.getJsonObject("attributes");
            if (attributes.containsKey("webid")) {
                m.add(m.createLiteralStatement(s, HAL.webid, m.createResource(attributes.getJsonArray("webid").getString(0))));
            }
        }
        return m;
    }
    
    public Model ParseUsers(String json) {
        System.out.println(json);
        JsonReader jr = Json.createReader(new StringReader(json));
        JsonArray ja = jr.readArray();
        Model m = ModelFactory.createDefaultModel(); 
        for (int i=0; i<ja.size(); i++) {
            m.add(ParseUser(ja.getJsonObject(i)));
        }
        return m;
    }

    public Model ParseGroups(String json, HashMap<String,String> map) {
        System.out.println(json);
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
