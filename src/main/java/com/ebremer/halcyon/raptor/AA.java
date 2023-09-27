package com.ebremer.halcyon.raptor;

import com.ebremer.beakgraph.ng.BeakGraph;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.ebremer.beakgraph.ng.BGDatasetGraph;
import com.ebremer.beakgraph.ng.StopWatch;
import com.ebremer.ns.GS;
import com.ebremer.ns.HAL;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class AA {
    
    public static void main(String[] args) throws IOException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        logger.setLevel(Level.OFF);
        
        StopWatch total = StopWatch.getInstance();
        StopWatch sw = StopWatch.getInstance();
        File f = new File("/AAA/wow-X4.zip");

        BeakGraph g = new BeakGraph(f.toURI());
        BGDatasetGraph bg = new BGDatasetGraph(g);
        Dataset ds = DatasetFactory.wrap(bg);
        sw.Lapse("Data Initialization").reset();
        /*
        Random rnd = new Random();
        long start = System.nanoTime();
        
        int num = 100;
        int maxngnum = 23000;
        for (int u=0; u<num; u++)  {
            int rr = rnd.nextInt(0, maxngnum);
            g.getReader().setCurrentGraph(rr);
            Model m = ModelFactory.createModelForGraph(g);
            Model s = ModelFactory.createDefaultModel();
            s.add(m);
           //System.out.println(s.size());
            //RDFDataMgr.write(System.out, s, Lang.TURTLE);
        }
        double time = (System.nanoTime() - start);
        time = time / 1000000d;
        time = time / ((double) num);
        System.out.println(time);*/
        
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select *
            where {
            graph ?g {
                ?geometry geo:asWKT ?polygon .
                ?feature geo:hasGeometry ?geometry;
                    a ?type .
                    ?type a ?class;
                          hal:hasProbability ?probability
            }
            }
            """
        );
        pss.setNsPrefix("geo", GS.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("g", HAL.NS+"grid/0/151/58");
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);        
        ResultSet rs = qe.execSelect();
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2.setColor(Color.BLUE);
        int bx = 151*512;
        int by = 58*512;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        rs.forEachRemaining(qs->{
            String wow = qs.get("polygon").asLiteral().getString();
            Polygon p = Tools.WKT2Polygon(wow);
            Coordinate[] vertices = p.getCoordinates();
            int x1, y1, x2, y2;
            for (int i = 0; i < vertices.length - 1; i++) {
                x1 = (int) vertices[i].x;
                y1 = (int) vertices[i].y;
                x2 = (int) vertices[i + 1].x;
                y2 = (int) vertices[i + 1].y;
                x1 = x1 - bx;
                y1 = y1 - by;
                x2 = x2 - bx;
                y2 = y2 - by;
                g2.drawLine(x1, y1, x2, y2);
            }
        });
        sw.Lapse("BufferedImage generated");
        g2.dispose();
        ImageIO.write(image, "png", byteArrayOutputStream);
        sw.Lapse("PNG Image generated");
        total.Lapse("Total Time");
        try (FileOutputStream fos = new FileOutputStream("D:\\AAA\\image.png")) {
            fos.write(byteArrayOutputStream.toByteArray());
        }
    }
}
