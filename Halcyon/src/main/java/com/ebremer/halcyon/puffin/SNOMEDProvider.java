package com.ebremer.halcyon.puffin;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.SchemaDO;
import org.wicketstuff.select2.ChoiceProvider;
import org.wicketstuff.select2.Response;

/**
 *
 * @author erich
 */
public class SNOMEDProvider extends ChoiceProvider<Resource> {
    private static final long serialVersionUID = 1L;
    private static final String api = "https://browser.ihtsdotools.org/snowstorm/snomed-ct/browser/MAIN/2023-09-01/descriptions?&limit=100&active=true&conceptActive=true&lang=english&groupByConcept=true&term=";
    private final HashMap<Node,String> names;
    
    public SNOMEDProvider() {
        names = new HashMap<>();
    }

    @Override
    public String getDisplayValue(Resource choice) {
        if (choice.hasProperty(SchemaDO.name)) {
            return choice.getProperty(SchemaDO.name).getObject().asLiteral().getString()+" <"+choice.getURI()+">";
        }
        return "Name Unknown";
    }

    @Override
    public String getIdValue(Resource choice) {
        if (choice.hasProperty(SchemaDO.name)) {
            names.put(choice.asNode(),choice.getProperty(SchemaDO.name).getObject().asLiteral().getString());
        }
        return choice.getURI();
    }

    @Override
    public void query(String string, int i, Response<Resource> response) {
        response.addAll(getList(string));
    }
    
    private List<Resource> getList(String xxx) {
        String string;
        try {
            string = URLEncoder.encode(xxx, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            string = "unknown";
        }
        List<Resource> list = new ArrayList<>(100);
        Model m = ModelFactory.createDefaultModel();
        HttpClient client = HttpClient.newBuilder()
            .version(Version.HTTP_1_1)
            .followRedirects(Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                .uri(new URI(api+string))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            if (response.statusCode()==200) {
                JsonReader jr = Json.createReader(new StringReader(response.body()));
                JsonObject jo = jr.readObject();
                jo.getJsonArray("items").forEach(jv->{
                    JsonObject jj = (JsonObject) jv;
                    Resource rr = m.createResource("http://snomed.info/id/"+jj.getJsonObject("concept").getString("id"))
                    .addProperty(SchemaDO.name, jj.getJsonObject("concept").getJsonObject("fsn").getString("term"));
                    list.add(rr);
                });
            }
        } catch (URISyntaxException ex) {
            Logger.getLogger(SNOMEDProvider.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SNOMEDProvider.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(SNOMEDProvider.class.getName()).log(Level.SEVERE, null, ex);
        }
        return list;
    }

    @Override
    public Collection<Resource> toChoices(Collection<String> ids) {
        List<Resource> list = new ArrayList<>(ids.size());
        Model m = ModelFactory.createDefaultModel();
        //System.out.println("toChoices "+ids.size());
        ids.forEach(s->{
            System.out.println("RRRRRRRRRRRRR =====================>> "+s);
            Resource rrr = m.createResource(s);
            if (names.containsKey(rrr.asNode())) {
                rrr.addProperty(SchemaDO.name, names.get(rrr.asNode()));
            } else {
                rrr.addProperty(SchemaDO.name, "Name Unknown");
            }
            list.add(rrr);
        });
        return list;
    }
    
    @Override
    public void detach() {
       // System.out.println("DETACHING......");
    }
}
