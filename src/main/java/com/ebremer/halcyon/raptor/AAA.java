package com.ebremer.halcyon.raptor;

import com.ebremer.beakgraph.ng.BG;
import com.ebremer.beakgraph.ng.BG.PropertyAndDataType;
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.XSD;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.ebremer.beakgraph.ng.BGDatasetGraph;
import com.ebremer.beakgraph.ng.SpecialProcess;
import java.util.zip.GZIPInputStream;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class AAA {
    
    public static void main(String[] args) throws IOException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (Logger logger : loggerContext.getLoggerList()) {
            System.out.println("Logger: " + logger.getName());
        }
        Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        
        logger.setLevel(Level.OFF);
        File f = new File("/AAA/wow-X6.zip");
        //File x = new File("D:\\tcga\\cvpr-data\\rdf\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.ttl.gz");
        //File x = new File("/AAA/smallGS.ttl.gz");
        File x = new File("/AAA/fc.ttl.gz");
        
        Dataset dsi = DatasetFactory.create();
        try (
            FileInputStream fis = new FileInputStream(x);
            GZIPInputStream gis = new GZIPInputStream(fis);
        ) {
            RDFDataMgr.read(dsi.getDefaultModel(), gis, Lang.TURTLE);
        }
        ArrayList<PropertyAndDataType> list = new ArrayList<>();
        list.add(new PropertyAndDataType(HAL.low.getURI(), XSD.xlong));
        list.add(new PropertyAndDataType(HAL.high.getURI(), XSD.xlong));
        list.add(new PropertyAndDataType(HAL.hasRange.getURI(), null));
        ArrayList<SpecialProcess> specials = new ArrayList<>();
        specials.add(new HilbertSpecial(150000,150000));
        BG.getBuilder()
            .dataset(dsi)
            .handle(list)
            .setProcess(new HilbertProcess(150000,150000,512,512))
            .Extra(specials)
            .file(f)
            .build();
        BeakGraph g = new BeakGraph(f.toURI());
        
        Random rnd = new Random();
        long start = System.nanoTime();
        int num = 1;
        int maxngnum = 1;
        for (int u=0; u<num; u++)  {
            int rr = rnd.nextInt(0, maxngnum);
            g.getReader().setCurrentGraph(rr);
            Model m = ModelFactory.createModelForGraph(g);
            Model s = ModelFactory.createDefaultModel();
            s.add(m);
           System.out.println(s.size());
       //     RDFDataMgr.write(System.out, s, Lang.TURTLE);
        }
        double time = (System.nanoTime() - start);
        time = time / 1000000d;
        time = time / ((double) num);
        System.out.println(time);
        //Model m = ModelFactory.createModelForGraph(g);
    //    BGDatasetGraph bg = new BGDatasetGraph(g);
//        Dataset ds = DatasetFactory.wrap(bg);
  //      System.out.println("================================ END ==============================================");
    //    Model done = ds.getDefaultModel();
      //  RDFDataMgr.write(System.out, done, Lang.TURTLE);
    }
}
