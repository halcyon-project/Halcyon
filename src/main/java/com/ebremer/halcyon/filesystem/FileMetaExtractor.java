package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.datum.EB;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LOC;
import com.ebremer.halcyon.utils.Hash;
import com.ebremer.ns.EXIF;
import com.ebremer.rocrate4j.ROCrateReader;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataLevel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public final class FileMetaExtractor {
    private final File file;
    private final Dataset ds;
    private final Model coremeta;
    private final Model m;
    private final Resource s;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public FileMetaExtractor(File file) {
        this.file = file;
        this.ds = DatasetFactory.createGeneral();
        m = ModelFactory.createDefaultModel();
        coremeta = ds.getDefaultModel();
        this.s = m.createResource(EB.fix(file));
        ds.addNamedModel(EB.fix(file), m);
        Process();
    }
    
    public Dataset getDataset() {
        return ds;
    }
    
    public Model getMeta() {
        return m;
    }

    public Model getCoreMeta() {
        return coremeta;
    }
    
    private void Process() {
        String f = file.toString();
        String extension = f.substring(f.lastIndexOf(".")+1).toLowerCase();
        //CalculateMD5();
        switch (extension) {
            case "zip":
                Model z;
                try {
                    String fixb = EB.fix(file);
                    String fixe = EB.fix(file)+"/";
                    System.out.println(fixb+" ----> "+fixe);
                    //ROCrate roc = new ROCrate(file);
                    //ROCrateReader roc = new ROCrateReader(fixb, file.toURI());
                    ROCrateReader roc = new ROCrateReader(file.toURI());
                    //z = roc.getManifest(fixe);
                    z = roc.getManifest();
                    System.out.println("LOADED : "+z.size());
                    System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
                    RDFDataMgr.write(System.out, z, RDFFormat.TURTLE_PRETTY);
                    System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
                    UpdateRequest update = UpdateFactory.create();
                    ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                        delete {?s ?p ?o}
                        insert {?neo ?p ?o}
                        where {
                            ?s ?p ?o
                        }
                    """);
                    pss.setIri("s", fixe);
                    pss.setIri("neo", fixb);
                    update.add(pss.toString());
                    pss = new ParameterizedSparqlString("""
                        delete {?s ?p ?neo}
                        insert {?s ?p ?o}
                        where {
                            ?s ?p ?o
                        }
                    """);
                    pss.setIri("o", fixe);
                    pss.setIri("neo", fixb);
                    update.add(pss.toString());
                    UpdateAction.execute(update, z);
                    m.add(z);
                    System.out.println("Extracted ========================================================\n");
                    RDFDataMgr.write(System.out, m, Lang.TURTLE);
                    System.out.println("Extracted END END END ============================================");
                   // roc.close();
                } catch (IOException ex) {
                    Logger.getLogger(FileMetaExtractor.class.getName()).log(Level.SEVERE, null, ex);
                }
                break;
            case "svs":
            case "tif":
            case "ndpi":
                m.add(ImageFileExtractor());
                CalculateMD5();         
                break;
            case "":
                System.out.println("File with no extension.  Not processed");
                break;
            default:
                System.out.println("Unknown file type : "+extension);
        }
        ZonedDateTime dateTime = ZonedDateTime.now();
        m.addLiteral(s, HAL.dateRegistered, dateTime.format(formatter));
        m.add(s, SchemaDO.name, s.getLocalName());
        m.addLiteral(s,SchemaDO.contentSize,file.length());
    }
    
    public void CalculateMD5() {
        try {
            long now = System.nanoTime();
            String hash = Hash.GetMD5(file);
            System.out.println("Time = "+((System.nanoTime()-now)/1000000000d));
            m.add(s,LOC.md5,hash);
            m.add(s,OWL.sameAs,coremeta.createResource("urn:md5:"+hash));
            //m.add(s,OWL.sameAs,coremeta.createResource("urn:sha256:"+Hash.));
        } catch (Exception ex) {
            Logger.getLogger(FileMetaExtractor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public Model ImageFileExtractor() {
        DynamicMetadataOptions options = new DynamicMetadataOptions();
        options.setValidate(false);
        options.setMetadataLevel(MetadataLevel.NO_OVERLAYS);
        ImageReader reader = new ImageReader();
        reader.setMetadataOptions(options);
        reader.setOriginalMetadataPopulated(false);
        try {
            reader.setId(file.getPath());
            Resource ss = m.createResource(EB.fix(file));
            m.add(ss,RDF.type,SchemaDO.ImageObject);
            m.addLiteral(ss,EXIF.width,reader.getSizeX());
            m.addLiteral(ss,EXIF.height,reader.getSizeY());
        } catch (FormatException | IOException ex) {
            
        }
        return m;
    }
    
    /*
    public static void main(String[] args) {
        DebugTools.setRootLevel("WARN");
        File f = new File("D:\\HalcyonStorage\\demo2\\coad_CM-5348\\Cdysplasia_heatmap_TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip");
        FileMetaExtractor fe = new FileMetaExtractor(f);
        RDFDataMgr.write(System.out, fe.getDataset(), RDFFormat.TRIG_PRETTY);     
    } 
*/
}