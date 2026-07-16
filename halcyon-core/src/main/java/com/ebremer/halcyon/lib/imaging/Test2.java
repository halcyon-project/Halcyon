package com.ebremer.halcyon.lib.imaging;

import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 *
 * @author erich
 */
public class Test2 {

    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String args[]) throws Exception {
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, "E:\\tcga\\cvpr-data\\rdf\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.ttl.gz", Lang.TURTLE);
        System.out.println(m.size());
        //TileEngine te = TileEngine.Builder.newInstance("D:\\HalcyonStorage\\tcga\\brca\\tif\\TCGA-E2-A1B1-01Z-00-DX1.7C8DF153-B09B-44C7-87B8-14591E319354.tif")
        TileEngine te = TileEngine.Builder.newInstance("D:\\HalcyonStorage\\tcga\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.svs")
            .setTileSizeX(2000)
            .setTileSizeY(2000)
            .build();
        
        te.stream(m).parallel()
            .forEach(t->{
                System.out.println(t);
                //t.pattern().Write(Path.of("/dump"));
                Path file = Path.of("/dump", "mask-"+t.pattern().getTileRequest().getRegion().getX()+"-"+t.pattern().getTileRequest().getRegion().getY()+"-"+t.pattern().getTileRequest().getRegion().getWidth()+"-"+t.pattern().getTileRequest().getRegion().getHeight()+".png");
                /*
                try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                    ImageIO.write(t.mask(), "png", fos);
                } catch (IOException ex) {
                    Logger.getLogger(Tile.class.getName()).log(Level.SEVERE, null, ex);
                }
*/
            });
    }
}
