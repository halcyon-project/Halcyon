package com.ebremer.halcyon.converters;

import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.SNO;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class UPENN {
    private final int width;
    private final int height;
    private final String md5;
    private final Resource classuri;
    private Model m = ModelFactory.createDefaultModel();
    private final String caname;
    
    public UPENN(String caname, String md5, int width, int height, Resource classuri) {
        this.height = height;
        this.width = width;
        this.md5 = md5;
        this.classuri = classuri;
        this.caname = "https://www.upenn.edu/PipeLine/"+caname;
    }
    
    public void DumpModel(Model m, Path file, boolean compress) {
        if (!file.getParent().toFile().exists()) file.getParent().toFile().mkdirs();
        try {
            OutputStream fos;
            if (compress) {
                fos = new GZIPOutputStream(new FileOutputStream(file.toFile()+".gz"));
            } else {
                fos = new FileOutputStream(file.toFile());
            }
            Context context = new Context();
            context.setTrue(RIOT.symTurtleOmitBase);
            RDFWriter.create()
                .source(m)
                .context(context)
                .lang(Lang.TURTLE)
                .base("")
                .output(fos); 
            fos.flush();
            fos.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        }
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
    }
    
    public void AddPolygon(Model m, String wkt, Float certainty) {
        Resource body = m.createResource();
        Resource target = m.createResource();
        Resource anno = m.createResource();
        anno.addProperty(RDF.type, OA.Annotation)
            .addProperty(OA.hasBody, body)
            .addProperty(OA.hasSelector, target);
        target.addProperty(RDF.type, OA.FragmentSelector)
            .addProperty(OA.hasSource, m.createResource(md5))
            .addLiteral(RDF.value, wkt);
        body.addProperty(HAL.assertedClass, classuri)
            .addLiteral(HAL.hasCertainty, certainty);
    }
    
    public Model Rename(Model m, String neo) {
        UpdateRequest request = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            delete {?s a so:CreateAction; ?p ?o}                                                          
            insert {?neo a so:CreateAction; ?p ?o}
            where {?s a so:CreateAction; ?p ?o}
            """);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("neo", neo);
        request.add(pss.toString());
        UpdateAction.execute(request,m);
        return m;
    }
    
    
    public void Process(File xsrc) throws FileNotFoundException {
        String dest = xsrc.getParent()+File.separatorChar+xsrc.getName().substring(0, xsrc.getName().length()-4)+".ttl";
        FileInputStream fis = new FileInputStream(xsrc.getParentFile().getParent()+File.separatorChar+"meta.ttl");
        RDFParser.create()
            .source(fis)
            .base("")
            .lang(Lang.TTL)
            .parse(m);
        //m = Rename(m, caname);
        ResIterator ri = m.listResourcesWithProperty(RDF.type, SchemaDO.CreateAction);
        Resource ca;
        if (ri.hasNext()) {
            ca = ri.next();
        } else {
            throw new Error("CANT FIND CreateAction");
        }
        Resource kk = m.createResource("urn:md5:"+md5);
        kk.addLiteral(EXIF.width, width).addLiteral(EXIF.height, height);
        m.add(ca, SchemaDO.object, kk);
        m.setNsPrefix("hal", HAL.NS);
        DumpModel(m,Path.of(dest),false);     
    }
    
    public void MainProcess(File file) throws FileNotFoundException, IOException {
        InputStream is = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        reader.readLine();
        while ((line = reader.readLine()) != null) {
            //System.out.println(line);
            String[] pline = line.split(",");
            Float prob = Float.parseFloat(pline[3]);
            String[] t = pline[0].split(".svs_");
            String b = t[1].substring(0, t[1].length()-4);
            String[] coord = b.split("_");
            int x = Integer.parseInt(coord[0]);
            int y = Integer.parseInt(coord[1]);
            int w = Integer.parseInt(coord[2]);
            int h = Integer.parseInt(coord[3]);
            String poly = "POLYGON (("+x+" "+y+", "+(x+w)+" "+y+", "+(x+w)+" "+(y+h)+", "+x+" "+(y+h)+"))";
            //System.out.println(poly);
            AddPolygon(m, poly, prob);
        }   
    }
    
    public void Cool(String src) throws IOException {
        File file = new File(src);
        MainProcess(file);
        Process(file);        
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        // F1 - File file = new File("D:\\Halcyon\\upenn\\final_predictions_with_averaged_probabilities.csv");
        // F1 - UPENN upenn = new UPENN("urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes);
        new UPENN("Lymphocytes/site1","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site1\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site2","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site2\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site3","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site3\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site4","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site4\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site5","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site5\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site6","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site6\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site7","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site7\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site8","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site8\\final_predictions_with_averaged_probabilities.csv");
        //new UPENN("Lymphocytes","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\site9\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/Consensus","urn:md5:0fc977067d714b8cbc571266b138bdd7", 21987, 17849, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-80-5608-01Z-00-DX1.CB85BA53-AF00-4C7D-8489-C2FF0F4F49AB.svs\\Consensus\\final_predictions_with_averaged_probabilities.csv");
        
        new UPENN("Lymphocytes/site1","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site1\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site2","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site2\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site3","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site3\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site4","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site4\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site5","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site5\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site6","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site6\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site7","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site7\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/site8","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site8\\final_predictions_with_averaged_probabilities.csv");
        //new UPENN("Lymphocytes","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\site9\\final_predictions_with_averaged_probabilities.csv");
        new UPENN("Lymphocytes/Consensus","urn:md5:6acdb49b58fc57c2041e2e84edcd5d03", 59759, 37971, SNO.Lymphocytes).Cool("D:\\upenn\\TCGA-AO-A0JC-01Z-00-DX1.C8DD421B-9799-4FE7-9224-5EAC6ED1028E.svs\\Consensus\\final_predictions_with_averaged_probabilities.csv");        
        
    }
    
}
