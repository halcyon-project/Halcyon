package com.ebremer.halcyon.lib.shacl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;

/**
 *
 * @author erich
 */
public class GeoSPARQL {
    private Shapes shapes = null;
    private final Model shacl;
    
    public GeoSPARQL() {
        shacl = ModelFactory.createDefaultModel();
        ClassLoader classLoader = GeoSPARQL.class.getClassLoader();
        try (InputStream fis = classLoader.getResourceAsStream("geosparqlshacl.ttl")) {
            RDFDataMgr.read(shacl, fis, Lang.TURTLE);
           // RDFDataMgr.write(System.out, shacl, Lang.TURTLE);
            shapes = Shapes.parse(shacl);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(GeoSPARQL.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(GeoSPARQL.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public boolean validate(Model m) {
        ValidationReport report = ShaclValidator.get().validate(shapes, m.getGraph());
        ShLib.printReport(report);
        return report.conforms();
    }
    
    public static void main(String[] args) throws FileNotFoundException {
        GeoSPARQL geosparql = new GeoSPARQL();
        File file = new File("D:\\tcga\\cvpr-data\\rdf\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.ttl");
        FileInputStream fis = new FileInputStream(file);
        Model test = ModelFactory.createDefaultModel();
        RDFDataMgr.read(test, fis, Lang.TURTLE);
        System.out.println(test.size());
        geosparql.validate(test);
    }
    
}
