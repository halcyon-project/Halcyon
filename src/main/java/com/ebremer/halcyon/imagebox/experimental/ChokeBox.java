/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.imagebox.experimental;

import com.ebremer.halcyon.imagebox.HTTPIRandomAccess;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.DebugTools;
import loci.common.Location;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.tiff.IFD;
import loci.formats.tiff.IFDList;

/**
 *
 * @author erich
 */
public class ChokeBox {
    
    public static void main(String[] args) throws FormatException, IOException {
        DebugTools.enableLogging("ERROR");
        //String wow = "http://localhost:8080/HalcyonStorage/atoz/M22384-A3-HE-multires.tif";
        //HTTPIRandomAccess4 bbb = new HTTPIRandomAccess4(wow);
        //String getthis = "charm";
        //Location.mapFile(getthis, bbb); 
        
        IFormatReader reader = new TiffReader();
        reader.setId("/HalcyonStorage/atoz/M22384-A3-HE-multires.tif");
        //reader.setId(getthis);
      //  reader.getSeriesCount();
        //TiffParser p = reader.getTiffParser();
        //IFDList la = p.getMainIFDs();
        //System.out.println("getSeriesCount() = "+reader.getSeriesCount());
        
        /*
        System.out.println(reader.getResolutionCount());
        MinimalTiffReader r = new MinimalTiffReader();
        r.setFlattenedResolutions(false);
        r.setId("/atoz/M22384-A3-HE-multires.tif");
        int c = r.getSeriesCount();
       
        System.out.println("# of series : "+c);
        
       IFDList f = r.getThumbnailIFDs();
       f.forEach(u-> {
            System.out.println("W : "+u.getIFDValue(IFD.IMAGE_WIDTH));
            System.out.println("H : "+u.getIFDValue(IFD.IMAGE_LENGTH));           
       });
*/
    }
    
}
