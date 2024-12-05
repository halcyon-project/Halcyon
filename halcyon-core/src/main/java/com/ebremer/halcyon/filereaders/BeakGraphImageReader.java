package com.ebremer.halcyon.filereaders;

import com.ebremer.beakgraph.Params;
import com.ebremer.beakgraph.core.BeakGraph;
import com.ebremer.beakgraph.pool.BeakGraphPool;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeakGraphImageReader extends AbstractImageReader {
    private static final Logger logger = LoggerFactory.getLogger(BeakGraphImageReader.class);
    private ImageMeta meta;
    private final URI uri;
    private final URI base;
    private static final Integer METAVERSION = 0;
    private static final Pattern pattern = Pattern.compile("(\\d+)\\s+(\\d+)");

    public BeakGraphImageReader(URI uri, URI base) throws IOException {
        logger.trace("BeakGraphImageReader {} {}", uri, base);
        this.uri = uri;
        if ( base == null ) {
            this.base = uri;
        } else {
            this.base = base;
        }
        BeakGraph bg = null;
        try {
            bg = BeakGraphPool.getPool().borrowObject(uri);
            Dataset ds = bg.getDataset();
            ParameterizedSparqlString pss = new ParameterizedSparqlString(
                """
                select ?width ?height
                where { ?geo exif:width ?width; exif:height ?height }
                """
            );
            pss.setNsPrefix("exif", EXIF.NS);
            int width, height;
            try (QueryExecution qexec = QueryExecutionFactory.create(pss.asQuery(), ds)) {
                ResultSet rs = qexec.execSelect();
                if (rs.hasNext()) {
                    QuerySolution qs = rs.next();
                    width = qs.getLiteral("width").getInt();
                    height = qs.getLiteral("height").getInt();                    
                } else {
                    throw new Error("missing image height/width!");
                }
            }        
            ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, width, height)
                .filter(false)
                .setTileSizeX(Params.GRIDTILESIZE)
                .setTileSizeY(Params.GRIDTILESIZE);
            int levels = getNumberOfLevels(bg);
            for (int s=0; s < levels; s++) {
                int sca = (int) Math.pow(2, s);
                builder.addScale(s, sca, (int) Math.round( width / sca ), (int) Math.round( height / sca ));
            }
            builder.setMeta(ModelFactory.createDefaultModel());
            meta = builder.build();
        } catch (Exception ex) {
            logger.error("Initialization failed for {}: {}", uri, ex.getMessage());
            throw new IOException("Could not initialize BeakGraphImageReader", ex);
        } finally {
            if (bg != null) {
                try {
                    BeakGraphPool.getPool().returnObject(uri, bg);
                } catch (Exception e) {
                    logger.error("Pool Return Error: " + e.getMessage());
                }
            }
        }
    }
    
    private int getNumberOfLevels(BeakGraph bg) {
        return (int) bg.getReader()
            .getDictionary()
            .streamPredicates()
            .filter(p->p.getURI().startsWith("https://halcyon.is/ns/asWKT"))
            .count();
    }
    
    @Override
    public int getMetaVersion() {
        return METAVERSION;
    }

    @Override
    public String getFormat() {
        return "h5";
    }

    @Override
    public void close() {}

    @Override
    public Model readTileMeta(ImageRegion region, Rectangle preferredsize) {
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth()/(double) preferredsize.width(),(double) region.getHeight()/ (double) preferredsize.height()));
        //Model m = readTileMeta(scale.Validate(region.scaleRegion(scale.scale())),scale.series());
        return ModelFactory.createDefaultModel();
    }

    /**
     * Simple helper to extract coordinates from WKT POLYGON strings
     * and convert them to relative tile coordinates.
     * @param wkt
     * @param offsetX
     * @param offsetY
     * @return 
     */
    private static Polygon parseWktToPolygon(String wkt, int offsetX, int offsetY) {
      //  IO.println(offsetX+" "+offsetY+"  "+wkt);
        Matcher matcher = pattern.matcher(wkt);
        List<Integer> xPoints = new ArrayList<>();
        List<Integer> yPoints = new ArrayList<>();       
        while (matcher.find()) {
            xPoints.add(Integer.parseInt(matcher.group(1)) - offsetX);
            yPoints.add(Integer.parseInt(matcher.group(2)) - offsetY);
        }        
        return new Polygon(
            xPoints.stream().mapToInt(i -> i).toArray(),
            yPoints.stream().mapToInt(i -> i).toArray(),
            xPoints.size()
        );
    }
    
    private static String polygonToString(Polygon poly) {
        if (poly == null) return "null";        
        StringBuilder sb = new StringBuilder("Polygon: [");
        for (int i = 0; i < poly.npoints; i++) {
            sb.append("(").append(poly.xpoints[i]).append(", ").append(poly.ypoints[i]).append(")");
            if (i < poly.npoints - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
        
    @Override
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize) {
        logger.trace("{} {}", region, preferredsize);
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth()/(double) preferredsize.width(),(double) region.getHeight()/ (double) preferredsize.height()));
        return readTile(scale.Validate(region.scaleRegion(scale.scale())),scale.series());
    }    
    
    private BufferedImage readTile(ImageRegion ir, int series) {
        //logger.trace("{} {}", ir, series);
        int a1 = ir.getX() / Params.GRIDTILESIZE;
        int b1 = ir.getY() / Params.GRIDTILESIZE;
        int a2 = (int)  Math.ceil(((float) ( ir.getX() + ir.getWidth() )) / Params.GRIDTILESIZE );
        int b2 = (int)  Math.ceil(((float) ( ir.getY() + ir.getHeight() )) / Params.GRIDTILESIZE );
        BufferedImage bi = new BufferedImage(ir.getWidth(), ir.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setColor(Color.RED);
        g2d.setStroke(new BasicStroke(1));
        for (int i=a1; i<a2; i++) {
            for (int j=b1; j<b2; j++) {
                //logger.trace("gridding -> {} {} {}", i, j, series);
                ParameterizedSparqlString pss = new ParameterizedSparqlString(
                    """
                    select distinct ?geo ?wkt
                    where {
                        graph <urn:x-beakgraph:grid:?series:?x:?y> {
                            ?geo hal:asWKT?series ?wkt
                        }                
                    }
                    """
                );
                pss.setLiteral("series", series);
                pss.setLiteral("x", i);
                pss.setLiteral("y", j);
                pss.setNsPrefix("hal", HAL.NS);
                pss.setNsPrefix("geo", "http://www.opengis.net/ont/geosparql#");
                pss.setNsPrefix("geof", "http://www.opengis.net/def/function/geosparql/");
                BeakGraph bg = null;
                String wkt = null;
                try {
                    bg = BeakGraphPool.getPool().borrowObject(uri);
                    try (QueryExecution qexec = QueryExecutionFactory.create(pss.toString(), bg.getDataset())) {
                        ResultSet rs = qexec.execSelect();
                        if (rs.hasNext()) {
                            //int offx = ir.getX() + ( (i-a1) * Params.GRIDTILESIZE );
                            //int offy = ir.getY() + ( (j-b1) * Params.GRIDTILESIZE );
                            while (rs.hasNext()) {
                                QuerySolution qs = rs.next();
                                wkt = qs.getLiteral("wkt").getString();
                              //  logger.trace("tile -> {} {} {} {}", i, j, offx, offy);
                                g2d.fillPolygon( parseWktToPolygon( wkt, ir.getX(), ir.getY() ) );                                
                            }                            
                        }
                    }            
                }  catch (Exception ex) {
                    logger.error(wkt+"  "+ex.getMessage());                   
                    ex.printStackTrace();
                } finally {
                    if (bg != null) {
                        try {
                            BeakGraphPool.getPool().returnObject(uri, bg);
                        } catch (Exception e) {
                            logger.error("Pool Return Error : {}", e.getMessage());
                        }
                    }
                }
            }
        }
        g2d.dispose();
        return bi;
    }
        
    @Override
    public ImageMeta getImageMeta() {
        logger.trace("getImageMeta{} ", base);
        return meta;
    }

    @Override
    public Model getMeta(URI xuri) {
        logger.trace("getMeta {}", xuri);
        Model m = ModelFactory.createDefaultModel();
        m.createResource(URITools.fix(base))
            .addLiteral(HAL.filemetaversion, METAVERSION)
            .addLiteral(EXIF.width, meta.getWidth())
            .addLiteral(EXIF.height, meta.getHeight())
            .addProperty(RDF.type, SchemaDO.ImageObject)
            .addProperty(RDF.type, SchemaDO.Dataset);
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
        return m;
    }
    
    @Override
    public Model getMeta() {
        return getMeta(base);
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("h5");
        return set;
    }
}
