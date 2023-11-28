package com.ebremer.halcyon.FL;

import com.ebremer.beakgraph.ng.BGDatasetGraph;
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.beakgraph.ng.StopWatch;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.raptor.Objects.Scale;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;


/**
 *
 * @author erich
 */
public class TestHeatMap {
    
    public static void main(String[] args) throws IOException {
        Configurator.setLevel("com.ebremer.beakgraph.ng", Level.DEBUG);
        //Configurator.setRootLevel(Level.ALL);
        StopWatch sw = StopWatch.getInstance();
        File file = new File("D:\\tcga\\heatmaps\\coad\\TCGA-CM-5348-01Z-00-DX1.2AD0B8F6-684A-41A7-B568-26E97675CCE9.zip");
        BeakGraph bg = new BeakGraph(file.toURI());
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
       
        FL fl = new FLHeatmap(file.toURI());
        String iiif = fl.GetIIIFImageInfo("http://localhost:8888/iiif/?iiif=http://localhost:8888/HalcyonStorage/inceptv4/coad/TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip/info.json");
        //System.out.println("=======================================================================================================================\n"+iiif);
        //System.out.println(sw.Lapse("FL Loaded..."));
        List<Scale> scales = fl.getScales();
        Scale scale = scales.getFirst();
        BufferedImage bi = fl.readTile(new ImageRegion(0, 0, scale.width(), scale.height()), scale.scale());
        File outputfile = new File("D:\\tcga\\saved.png");
        ImageIO.write(bi, "png", outputfile);
        
        //Model ha = ModelFactory.createDefaultModel();
        //ha.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        //ha.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        //ha.setNsPrefix("geo", "http://www.opengis.net/ont/geosparql#");
        //ha.setNsPrefix("xmls", "http://www.w3.org/2001/XMLSchema#");
        //ha.setNsPrefix("prov", "http://www.w3.org/ns/prov#");
        //ha.setNsPrefix("so", "https://schema.org/");
        System.out.println(sw.Lapse("Tile generated"));
        //ha.add(ds.getDefaultModel());
        /*
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?s ?p ?o
            where {
                ?s hal:hasClassification ?o
                #graph ?g { ?s hal:hasClassification ?o }
            }
            """
        );
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("geo", "http://www.opengis.net/ont/geosparql#");
        pss.setNsPrefix("xmls", "http://www.w3.org/2001/XMLSchema#");
        pss.setNsPrefix("prov", "http://www.w3.org/ns/prov#");
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ha);
        ResultSet rs = qe.execSelect();
        ResultSetFormatter.out(System.out, rs);*/
       // RDFDataMgr.write(System.out, ha, RDFFormat.TURTLE_PRETTY);
    }
    
}
