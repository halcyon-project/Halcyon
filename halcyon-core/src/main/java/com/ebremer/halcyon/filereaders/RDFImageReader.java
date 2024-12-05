package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.PathMapper;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.GEO;
import com.ebremer.ns.PROVO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Erich Bremer
 */
public class RDFImageReader extends AbstractImageReader {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RDFImageReader.class);
    private final ImageMeta meta;
    private final URI uri;
    private static final int METAVERSION = 0;
    private Model m;
    
    public RDFImageReader(URI uri, URI base) throws IOException {
        logger.info("RDFImageReader(URI uri, URI base) {} {}", uri, base);
        this.uri = uri;
        m = ModelFactory.createDefaultModel();
        RDFFileReader reader = new RDFFileReader(uri, base);

        Resource subject = reader.getSubject();
        
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
        """
        select ?width ?height       
        where {
            ?s  a geo:FeatureCollection;
                prov:wasGeneratedBy [ a prov:Activity;
                                      prov:used ?image
                                    ] .
            ?image a sdo:ImageObject; exif:width ?width; exif:height ?height
        }
        """
        );
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("prov", PROVO.NS);
        pss.setNsPrefix("sdo", SchemaDO.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("exif", EXIF.NS);
        //pss.setIri("s", uri.toString());
        int width = 0;
        int height = 0;        
        try (QueryExecution qexec = QueryExecutionFactory.create(pss.toString(), subject.getModel())) {
            ResultSet rs = qexec.execSelect();
            if (rs.hasNext()) {
                QuerySolution qs = rs.next();
                width = qs.get("width").asLiteral().getInt();
                height = qs.get("height").asLiteral().getInt();
            }
        }
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, width, height)
            .setTileSizeX(width)
            .setTileSizeY(height);
        meta = builder.build(); 
    }

    @Override
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
        """
        construct {?s ?p ?o}
        where {
            ?s  a geo:FeatureCollection;
                rdfs:member [
                    a geo:Feature;
                    geo:hasGeometry [
                        geo:asWKT ?wkt;
                        hal:classification ?classification
                    ]
                ]
        }
        """
        );
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setIri("s", uri.toString());
        //try (QueryExecution qexec = QueryExecutionFactory.create(pss.toString(), r.getModel())) {
          //  ResultSet rs = qexec.execSelect();
        //}
        
        return null;
    }

    @Override
    public Model readTileMeta(ImageRegion region, Rectangle preferredsize) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public ImageMeta getImageMeta() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public int getMetaVersion() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public Model getMeta() {
        return meta.getRDF();
    }

    @Override
    public Model getMeta(URI uri) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String getFormat() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public Set<String> getSupportedFormats() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void close() throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
    public static void main(String[] args) throws IOException {
        
        URI uri = URI.create("https://localhost:8888/ldp/utah/Stack1-With-IHC/features0.ttl");
        
        
//        File settingsFile = new File("D:\\projects\\Halcyon\\Halcyon\\settings.ttl");
//        HalcyonSettings settings = HalcyonSettings.getSettings(settingsFile);
//        PathMapper pathMapper = PathMapper.getPathMapper(settings);
//        RDFFileReader r = new RDFFileReader(uri,pathMapper);
//        RDFDataMgr.write(System.out, r.getMeta(), Lang.TURTLE);  
        
        
        URI local = URI.create("file:///D:/HalcyonStorage/utah/Stack1-With-IHC/features0.ttl");
        RDFImageReader r2 = new RDFImageReader(local,uri);
        RDFDataMgr.write(System.out, r2.getMeta(), Lang.TURTLE); 
    }
    
}
