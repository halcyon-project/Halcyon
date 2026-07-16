package com.ebremer.halcyon.lib.imaging;

import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.lib.BackgroundDetector;
import com.ebremer.halcyon.lib.GeometryTools;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.lib.Tile;
import com.ebremer.halcyon.lib.TileRequestEngine;
import com.ebremer.halcyon.server.utils.ImageReaderPool;
import com.ebremer.halcyon.services.ServicesLoader;
import com.ebremer.halcyon.utils.HURI;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

/**
 *
 * @author erich
 */
public class TileEngine implements AutoCloseable {
    private final URI uri;
    private final ImageMeta meta;
    private final TileRequestEngine tre;
    private final int tileSizeX;
    private final int tileSizeY;
    private final int StrideX;
    private final int StrideY;
    private final boolean cache;
    private record Pair(int x, int y) {};
    private final boolean[][] mask;
    private final int maskx;
    private final int masky;
    public record Training(Tile pattern, BufferedImage mask) {};
    private record Anno(Pair pair, String classification, Polygon polygon) {};
    private final HashMap<Pair,Set<Anno>> annos;

    private TileEngine(Builder builder) throws Exception {
        this.uri = builder.uri;
        this.annos = new HashMap<>();
        this.tileSizeX = builder.tileSizeX;
        this.tileSizeY = builder.tileSizeY;
        this.StrideX = builder.StrideX;
        this.StrideY = builder.StrideY;
        this.cache = builder.cache;
        ImageReader ir = ImageReaderPool.getPool().borrowObject(uri);
        this.meta = ir.getImageMeta();
        this.maskx = ((int) meta.getWidth()/tileSizeX ) + 1;
        this.masky = ((int) meta.getHeight()/tileSizeY ) + 1;
        System.out.println("MASK : "+maskx+" "+masky);
        BufferedImage thumb = ir.readTile(new ImageRegion(0,0,meta.getWidth(),meta.getHeight()), new Rectangle(maskx,masky));
        BackgroundDetector.Dump(thumb, Path.of("/dump/thumb.png"));
        int tolerance = 16;
        BufferedImage bimask = BackgroundDetector.getMask(thumb, tolerance);
        BackgroundDetector.Dump(bimask, Path.of("/dump/mask.png"));        
        this.mask = BackgroundDetector.getBooleanMask(thumb, tolerance);
        ImageReaderPool.getPool().returnObject(uri, ir);
        tre = TileRequestEngine.getInstance();
    }
    
    @Override
    public void close() throws Exception {
        tre.shutdown();
    }
    
    private Stream<Pair> streamPairs(Polygon[] polygons, Options options) {
        List<Stream> list = Collections.synchronizedList(new ArrayList<>());
        Arrays.stream(polygons).parallel().forEach(p -> {
            Envelope bb = p.getEnvelopeInternal();
            list.add(IntStream
                .iterate((int) bb.getMinX(), n -> n + this.StrideX)
                .takeWhile(n -> n < ((int) bb.getMaxX()) - this.StrideX + 1)
                .boxed()
                .flatMap(x -> IntStream
                                .iterate(((int) bb.getMinY()), n -> n + this.StrideY)
                                .takeWhile(n -> n < ((int) Math.ceil(bb.getMaxY())) - this.StrideY + 1)
                                .mapToObj(y -> new Pair(x,y)))
                );                
        });
        Stream<Pair> full = list.stream().reduce(Stream.empty(), Stream::concat);
        if (options.contains(Option.TissueOnly)) {
            return full.filter(pair -> mask[(int) (pair.x()/tileSizeX)][(int)(pair.y()/tileSizeY)]);
        }
        return full;
    }
    
    private Stream<Tile> Pairs2Tiles(Stream<Pair> pairs) {
       // return pairs.parallel()
         //       .map(pair -> tre.getTile(new ImageRegion(pair.x(),pair.y(),tileSizeX,tileSizeY), new Rectangle(tileSizeX,tileSizeY), cache, true))
           //     .filter(tile -> (tile!=null));
        return null;
    }
    
    public Stream<Tile> stream(Polygon[] polygons, Options options) {
        return Pairs2Tiles(streamPairs(polygons, options));
    }
    
    public Stream<Tile> stream(Polygon[] polygons) {
        return stream(polygons, Options.create());
    }
    
    private Polygon[] Everything() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate[] coordinates = new Coordinate[] {
            new Coordinate(0, 0),
            new Coordinate(0, meta.getHeight()),
            new Coordinate(meta.getWidth(), meta.getHeight()),
            new Coordinate(meta.getWidth(), 0),
            new Coordinate(0, 0)
        };
        LinearRing linearRing = geometryFactory.createLinearRing(coordinates);
        Polygon wsi = geometryFactory.createPolygon(linearRing, null);   
        Polygon[] array = {wsi};        
        return array;
    }

    public Stream<Tile> stream(Options options) {
        return stream(Everything(), options);
    }
    
    public Stream<Tile> stream() {
        return stream(Options.create());
    }

    public Stream<Training> stream(Model m) {
        AnnoFun(m);
        Stream<Training> stream = streamPairs(Everything(), Options.create())
        .parallel()
        .map(pair->{
            Polygon box = GeometryTools.getPolygon(pair.x(), pair.y(), tileSizeX, tileSizeY);
            int x = pair.x()/tileSizeX;
            int y = pair.y()/tileSizeY;
            int a = (pair.x()+tileSizeX)/tileSizeX;
            int b = (pair.y()+tileSizeY)/tileSizeY;
            Pair p1 = new Pair(x, y);
            Pair p2 = new Pair(a, y);
            Pair p3 = new Pair(a, b);
            Pair p4 = new Pair(x, b);
            final Set<Anno> as = new HashSet<>();
            annos.getOrDefault(p1, new HashSet<>()).forEach(ad->{
                if (box.intersects(ad.polygon())) {
                    as.add(new Anno(pair,ad.classification(),ad.polygon()));
                }
            });
            annos.getOrDefault(p2, new HashSet<>()).forEach(ad->{
                if (box.intersects(ad.polygon())) {
                    as.add(new Anno(pair,ad.classification(),ad.polygon()));
                }
            });
            annos.getOrDefault(p3, new HashSet<>()).forEach(ad->{
                if (box.intersects(ad.polygon())) {
                    as.add(new Anno(pair,ad.classification(),ad.polygon()));
                }
            });
            annos.getOrDefault(p4, new HashSet<>()).forEach(ad->{
                if (box.intersects(ad.polygon())) {
                    as.add(new Anno(pair,ad.classification(),ad.polygon()));
                }
            });
            if (!as.isEmpty()) {
                //System.out.println("COOL == " +as);
                //Tile tile = tre.getTile(new ImageRegion(pair.x(),pair.y(),tileSizeX,tileSizeY), new Rectangle(tileSizeX,tileSizeY), cache, true);
                Tile tile = null;
                final BufferedImage bi = new BufferedImage(tileSizeX, tileSizeY, BufferedImage.TYPE_INT_RGB);
                //final BufferedImage bi = GeometryTools.copyBufferedImage(tile.getBufferedImage());
                Graphics2D g = bi.createGraphics();               
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, tileSizeX, tileSizeY);
                as.stream().parallel().forEach(ax->{
                    switch (ax.classification()) {
                        default:
                            g.setColor(Color.black);
                            Polygon pp = GeometryTools.translatePolygon(ax.polygon(), ax.pair().x(), ax.pair().y());                           
                            g.fillPolygon(GeometryTools.convertJTSToAWTPolygon(pp));
                    }
                });
                g.dispose();
                return new Training(tile, bi);
            }
            return null;
        })
        .filter(t->(t!=null));        
        return stream;
    }
    
    private void AnnoFun(Model m) { 
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?classification ?wkt
            where {
            ?feature a geo:Feature;
                geo:hasGeometry [ geo:asWKT ?wkt ];
                hal:classification ?classification
            }
            """);
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("hal", HAL.NS);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),m).execSelect();
        rs.forEachRemaining(qs->{
            Polygon poly = GeometryTools.WKT2Polygon(qs.get("wkt").asLiteral().getString());
            String classification = qs.get("classification").toString();
            if (poly!=null) {
                Envelope e = poly.getEnvelopeInternal();
                Pair p1 = new Pair((int) (e.getMinX()/tileSizeX), (int) (e.getMinY()/tileSizeY));
                Pair p2 = new Pair((int) (e.getMaxX()/tileSizeX), (int) (e.getMinY()/tileSizeY));
                Pair p3 = new Pair((int) (e.getMaxX()/tileSizeX), (int) (e.getMaxY()/tileSizeY));
                Pair p4 = new Pair((int) (e.getMinX()/tileSizeX), (int) (e.getMaxY()/tileSizeY));
                Anno a1 = new Anno(p1, classification, poly);
                Anno a2 = new Anno(p2, classification, poly);
                Anno a3 = new Anno(p3, classification, poly);
                Anno a4 = new Anno(p4, classification, poly);
                Set<Anno> set = annos.getOrDefault(p1, (new HashSet<>()));
                set.add(a1);
                annos.put(p1, set);
                set = annos.getOrDefault(p2, (new HashSet<>()));
                set.add(a2);
                annos.put(p2, set);
                set = annos.getOrDefault(p3, (new HashSet<>()));
                set.add(a3);
                annos.put(p3, set);
                set = annos.getOrDefault(p4, (new HashSet<>()));
                set.add(a4);
                annos.put(p4, set);
            }
        });
    }    

    public static class Builder {
        protected URI uri;
        protected int tileSizeX;
        protected int tileSizeY;
        protected int StrideX = 0;
        protected int StrideY = 0;
        protected boolean cache = false;
        
        private Builder(URI uri) {
            ServicesLoader.init();
            FileReaderFactoryProvider.init(Builder.class.getClassLoader());
            this.uri = uri;
        }

        public Builder setStrideX(boolean cache) {
            this.cache = cache;
            return this;
        }
        
        public Builder setStrideX(int x) {
            this.StrideX = x;
            return this;
        }

        public Builder setStrideY(int y) {
            this.StrideY = y;
            return this;
        }
        
        public Builder setStrideY(int x, int y) {
            this.StrideX = x;
            this.StrideY = y;
            return this;
        }   

        public Builder setTileSizeX(int x) {
            this.tileSizeX = x;
            return this;
        }

        public Builder setTileSizeY(int y) {
            this.tileSizeY = y;
            return this;
        }        
        
        public TileEngine build() throws Exception {
            StrideX = (StrideX==0)?tileSizeX:StrideX;
            StrideY = (StrideY==0)?tileSizeY:StrideY;
            return new TileEngine(this);
        }
        
        public static Builder newInstance(URI uri) {
            return new Builder(HURI.of(uri));
        }
        
        public static Builder newInstance(File file) {
            return new Builder(HURI.of(file));
        }
        
        public static Builder newInstance(String file) {
            return new Builder(HURI.of(new File(file)));
        }
    }
}
