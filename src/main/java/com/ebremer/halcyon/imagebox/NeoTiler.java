package com.ebremer.halcyon.imagebox;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.document.RdfDocument;
import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.IIIF;
import static com.ebremer.halcyon.imagebox.IIIFUtils.IIIFAdjust;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static java.lang.Math.abs;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Collections;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.DebugTools;
import loci.common.Location;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.MetadataTools;
import loci.formats.gui.AWTImageTools;
import loci.formats.in.NDPIReader;
import loci.formats.in.SVSReader;
import com.ebremer.halcyon.imagebox.experimental.TiffReader;
import java.nio.file.Path;
import java.util.HashMap;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.services.OMEXMLService;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.primitives.PositiveInteger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 *
 * @author erich
 */
public class NeoTiler {
    IFormatReader reader;
    private ServiceFactory factory;
    private OMEXMLService service;
    private MetadataStore store;
    private IMetadata meta;
    private OMEXMLMetadataRoot newRoot;
    private int lowerbound = 0;
    private int upperbound = 0;
    private int numi;
    private final int iWidth;
    private final int iHeight;
    private final int[] px;
    private final int[] py;
    private final int[] pr;
    private final int[] pi;
    private double mppx;
    private double mppy;
    private boolean borked = false;
    private String status = "";
    private long lastaccessed;
    private CoreMetadata big;
    private String url;
    private String cookie = null;
    
    public NeoTiler(String f) {
        DebugTools.enableLogging("ERROR");
        lastaccessed = System.nanoTime();
        String getthis;
        HalcyonSettings settings = HalcyonSettings.getSettings();
        String mainpath = settings.getHostName();
        Path x = null;
        if (f.startsWith(mainpath)) {
            String cut = f.substring(mainpath.length());
            HashMap<String, String> mappings = settings.getmappings();
            for (String key : mappings.keySet()) {
                if (cut.startsWith(key)) {
                    String chunk = cut.substring(key.length());
                    x = Path.of(mappings.get(key), chunk);
                }
            }
        }
        if (x !=null) {
            getthis = x.toString();
        } else if ((f.startsWith("https://")||f.startsWith("http://"))) {
            HTTPIRandomAccess bbb = new HTTPIRandomAccess(f);
            bbb.init();
            getthis = "charm";
            Location.mapFile(getthis, bbb); 
        } else {
            getthis = f;
        }
        String fileType = f.substring(f.lastIndexOf('.') + 1);
        reader = switch (fileType) {
            case "ndpi" -> new NDPIReader();
            case "svs" -> new SVSReader();
            case "tif" -> new TiffReader();
            //case "vsi" -> new CellSensReader();
            default -> null;
        }; // VSI will require more work.  It is broken at the moment
        reader.setGroupFiles(true);
        reader.setMetadataFiltered(true);
        reader.setOriginalMetadataPopulated(true);
        try {
            factory = new ServiceFactory();
            service = factory.getInstance(OMEXMLService.class);
            reader.setMetadataStore(service.createOMEXMLMetadata(null, null));
            reader.setId(getthis);
            store = reader.getMetadataStore();
            MetadataTools.populatePixels(store, reader, false, false);
            reader.setSeries(0);
            String xml = service.getOMEXML(service.asRetrieve(store));
            meta = service.createOMEXMLMetadata(xml);
        } catch (DependencyException | ServiceException | IOException ex) {
            Logger.getLogger(NeoTiler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FormatException ex) {
            borked = true;
            status = ex.getMessage();
        }
        if (!borked) {
            newRoot = (OMEXMLMetadataRoot) meta.getRoot();
            numi = reader.getSeriesCount();
         //   System.out.println("getSeriesCount : "+numi);
            if (getthis.endsWith(".vsi")) {
                lowerbound = MaxImage(reader);
            }
            Hashtable<String, Object> hh = reader.getSeriesMetadata();
            if (hh.containsKey("MPP")) {
                double mpp = Double.parseDouble((String) hh.get("MPP"));
                mppx = mpp;
                mppy = mpp;
            }
            numi = numi - lowerbound;
            px = new int[numi];
            py = new int[numi];
            pr = new int[numi];
            pi = new int[numi];
            for (int j=0;j<numi;j++) {
                big = reader.getCoreMetadataList().get(j);
               // System.out.println(j+" >>> "+big.sizeX+","+big.sizeY+" aspect ratio : "+(((float) big.sizeX)/((float)big.sizeY)));
            }
            big = reader.getCoreMetadataList().get(lowerbound);
            float ratio = ((float) big.sizeX)/((float) big.sizeY);
            for (int j=lowerbound;j<(numi+lowerbound);j++) {
                big = reader.getCoreMetadataList().get(j);
                int offset = j-lowerbound;
                px[offset] = big.sizeX;
                py[offset] = big.sizeY;
                pr[offset] = px[lowerbound]/px[offset];
                pi[offset] = j;
                float mi = (((float) big.sizeX)/((float)big.sizeY));
                float off = abs((mi-ratio)/ratio);
                if (off>0.01) {
                    px[offset] = 1;
                }
            }
            SortImages();
            upperbound = lowerbound + 1;
            while ((upperbound<numi)&&(px[upperbound]>1024)) {
                upperbound++;
            }
            for (int j=0;j<numi;j++) {
                pr[j] = px[0]/px[j];
                //System.out.println(j+" >>> "+pi[j]+" "+pr[j]+"  "+px[j]+","+py[j]);
            }
            numi = upperbound - lowerbound;
            reader.setSeries(lowerbound);
            iWidth = reader.getSizeX();
            iHeight = reader.getSizeY();
        } else {
            iWidth = 0;
            iHeight = 0;
            px = null;
            py = null;
            pr = null;
            pi = null;
        }
    }
    
    public void SortImages() {
        boolean sorted = false;
        while (!sorted) {
            sorted = true;
            for (int i = 0; i<numi-1; i++) {
                if (px[i]<px[i+1]) {
                    int temp = px[i];
                    px[i] = px[i+1];
                    px[i+1] = temp;
                    temp = py[i];
                    py[i] = py[i+1];
                    py[i+1] = temp;
                    temp = pi[i];
                    pi[i] = pi[i+1];
                    pi[i+1] = temp;
                    sorted = false;
                }
            }
        }
    }
    
    public void setCookie(String cookie) {
        this.cookie = cookie;
    }
    
    public void setURL(String r) {
        //System.out.println("setURL : "+r);
        url = r;
    }
    
    public void close() {
        try {
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(NeoTiler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public int GetWidth() {
        return iWidth;
    }
    
    public int GetHeight() {
        return iHeight;
    }    
    
    public boolean isBorked() {
        return borked;
    }
    
    public String getStatus() {
        return status;
    }
    
    public long GetLastAccess() {
        return lastaccessed;
    }
    
    public void UpdateLastAccess() {
        lastaccessed = System.nanoTime();
    }

    public int MaxImage(IFormatReader SReader) {
        int ii = 0;
        int maxseries = 0;
        int maxx = Integer.MIN_VALUE;
        for (CoreMetadata c : SReader.getCoreMetadataList()) {
            if (c.sizeX>maxx) {
                maxseries = ii;
                maxx = c.sizeX;
            }
            ii++;
        }
        return maxseries;
    }

    public String GetImageInfo() {
        Model m = ModelFactory.createDefaultModel();
        Resource s = ResourceFactory.createProperty(url.substring(0, url.length()-10));      
        m.addLiteral(s, EXIF.height, iHeight);
        m.addLiteral(s, EXIF.width, iWidth);
        m.addLiteral(s, EXIF.xResolution, Math.round(10000/mppx));
        m.addLiteral(s, EXIF.yResolution, Math.round(10000/mppy));
        m.addLiteral(s, EXIF.resolutionUnit, 3);
        Resource scale = m.createResource();
        for (int j=upperbound;j>=0;j--) {
            reader.setSeries(j);
            Resource size = m.createResource();
            m.addLiteral(size,IIIF.width,reader.getSizeX());
            m.addLiteral(size,IIIF.height,reader.getSizeY());
            m.add(s,IIIF.sizes,size);
            m.addLiteral(scale, IIIF.scaleFactors,((int)(iWidth/reader.getSizeX())));
            m.add(s,IIIF.tiles,scale);
        }
        m.addLiteral(scale,IIIF.width,reader.getOptimalTileWidth());
        m.addLiteral(scale,IIIF.height,reader.getOptimalTileHeight());
        IIIFUtils.addSupport(s, m);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, m, Lang.NTRIPLES);
        String raw = new String(baos.toByteArray(), UTF_8);
        Document rdf;
        JsonArray ja;
        JsonLdOptions options = new JsonLdOptions();
        options.setOrdered(false);
        options.setUseNativeTypes(true);
        options.setOmitGraph(true);
        try {
            rdf = RdfDocument.of(new ByteArrayInputStream(raw.getBytes()));
            ja = JsonLd.fromRdf(rdf)
                .options(options)
                .get();
            Document contextDocument = JsonDocument.of(new ByteArrayInputStream(IIIF.CONTEXT.getBytes()));
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            baos = new ByteArrayOutputStream();
            JsonWriter out = writerFactory.createWriter(baos);
            JsonObject jo = JsonLd.compact(JsonDocument.of(ja), contextDocument)
                               .mode(JsonLdVersion.V1_1)
                               .options(options)
                               .get();
            //out.writeObject(jo);
            jo = JsonLd.frame(JsonDocument.of(jo), JsonDocument.of(new ByteArrayInputStream(("{\n" +
                    "  \"@context\": \"http://iiif.io/api/image/2/context.json\",\n" +
                    "  \"@embed\": \"@always\",\n" +
                    "  \"protocol\": \"http://iiif.io/api/image\",\n" +
                    "  \"profile\": {}\n" +
                    "}").getBytes())))              
                            .mode(JsonLdVersion.V1_1)
                            .options(options)
                            .get();
            out.writeObject(jo);
            return IIIFAdjust(IIIFUtils.NSFixes(new String(baos.toByteArray(), UTF_8)));
        } catch (JsonLdError ex) {
            Logger.getLogger(NeoTiler.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public BufferedImage FetchImage(int x, int y, int w, int h, int tx, int ty, String type) {
        //System.out.println("FetchImage : "+x+" "+y+" "+w+" "+h+" "+tx+" "+ty);
        int iratio = w/tx;
        int jj = 0;
        while ((jj<numi)&&(iratio>pr[jj])) {
           // System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> "+jj+"  "+pi[jj]+" "+pr[jj]+"   "+numi+"  "+iratio);
            jj++;
        }
        if (jj>numi) {jj--;}
        //System.out.println("selecting series : "+pi[jj]);
       	reader.setSeries(pi[jj]);
       	double rr = ((double) reader.getSizeX())/((double) iWidth);
        int gx=(int) (x*rr);
        int gy=(int) (y*rr);
        int gw=(int) (w*rr);
        int gh=(int) (h*rr);
        BufferedImage bi = GrabImage(gx,gy,gw,gh,type);
        float scale = (((float) tx)/((float) bi.getWidth()));
        if (Math.abs(scale-1.0f)>0.02) {
            //System.out.println("SCALE : "+scale+ "  "+gx+","+gy+","+gw+","+gh);
            BufferedImage target;
            AffineTransform at = new AffineTransform();
            at.scale(scale,scale);
            AffineTransformOp scaleOp =  new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            target = new BufferedImage((int)(gw*scale),(int)(gh*scale),bi.getType());
            scaleOp.filter(bi, target);
            return target;
        }
        return bi;
    }
    
    private BufferedImage GrabImage(int xpos, int ypos, int width, int height, String type) {
        //System.out.println("grab image : "+xpos+ " "+ypos+" "+width+" "+height+" === "+type);
        meta.setRoot(newRoot);
        meta.setPixelsSizeX(new PositiveInteger(width), 0);
        meta.setPixelsSizeY(new PositiveInteger(height), 0);
        byte[] buf;
        BufferedImage bb = null;
        try {
            buf = reader.openBytes(0, xpos, ypos, width, height);
            bb = AWTImageTools.makeImage(buf, reader.isInterleaved(), meta, 0);
        } catch (FormatException | IOException ex) {
            System.out.println("I'm dying....ERROR");
            Logger.getLogger(NeoTiler.class.getName()).log(Level.SEVERE, null, ex);
        } 
        return bb;
    }
}
