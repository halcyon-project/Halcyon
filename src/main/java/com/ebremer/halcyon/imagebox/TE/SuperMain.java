package com.ebremer.halcyon.imagebox.TE;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/**
 *
 * @author erich
 */
public class SuperMain {
    
    public static void main(String[] args) throws Exception {
        
        PluginLoader pluginLoader = new PluginLoader();
        List<AbstractImageReader> plugins = pluginLoader.getPlugins();
        for (AbstractImageReader plugin : plugins) {
            System.out.println("Services --> "+plugin.getFormat());
        }
        
       // TileRequestEnginePool pool = TileRequestEnginePool.getPool();
        //File file = new File("D:\\HalcyonStorage\\tcga\\brca\\TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.svs");
        //File file2 = new File("D:\\ATAN\\US720001A17-1.tif");
        File file2 = new File("D:\\ATAN\\src\\TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.svs");
        //File file2 = new File("D:\\ATAN\\src2\\ha.tif");
        //TileRequestEngine tpe = pool.borrowObject(file.toURI());
        //BufferedImage bi = tpe.getBufferedImage(new ImageRegion(30000,30000,2000,2000));
        //bi = tpe.getBufferedImage(new ImageRegion(30000,30000,2000,2000));
        
       // TileRequestEngine tpe2 = pool.borrowObject(file2.toURI());
        //BufferedImage bi2 = tpe2.getBufferedImage(new ImageRegion(30000,30000,2000,2000), new Rectangle(1000,1000));
        
        /*
        BufferedImage bi2 = tpe2.getBufferedImage(new ImageRegion(0,0,146162,104509), new Rectangle(1000,0), true);
        bi2 = tpe2.getBufferedImage(new ImageRegion(0,0,146162,104509), new Rectangle(0,3000), true);
        bi2 = tpe2.getBufferedImage(new ImageRegion(0,0,146162,104509), new Rectangle(0,3000), true);
        bi2 = tpe2.getBufferedImage(new ImageRegion(0,0,146162,104509), new Rectangle(0,3000), true);
        
        
        try {
            File outputfile = new File("D:\\ATAN\\saved.jpg");
            ImageIO.write(bi2, "jpg", outputfile);
            //File outputfile2 = new File("D:\\ATAN\\saved2.jpg");
            //ImageIO.write(bi, "jpg", outputfile2);
        } catch (IOException e) { }
*/
        SX sx = new SX(file2.toURI(),new Rectangle(350,350), new Rectangle(240,240));
        sx.getTiles()
                .forEach(t->{
                    //System.out.println(t.imageregion().getRegion());
                });
    }
}
