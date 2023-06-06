package com.ebremer.halcyon.imagebox;

import java.util.ArrayList;
import loci.formats.IFormatReader;
import loci.formats.in.NDPIReader;
import loci.formats.in.SVSReader;
import loci.formats.in.TiffReader;
import loci.formats.tiff.IFD;
import loci.formats.tiff.IFDList;

/**
 *
 * @author erich
 */
public class Stack {
    private ArrayList<IFD> neolist;
    public final int[] index;
    public final int[] width;
    public final int[] height;
    public final float[] ratio;
    private static final int TEMP = 7777777;
    
    public Stack(IFormatReader reader) {
        IFDList list = null;
        if (reader instanceof SVSReader r) {
            list = r.getIFDs();
            neolist = new ArrayList<>(list.size());
            for(int i=0; i<list.size();i++) {
                IFD ifd = list.get(i);
                if (ifd.containsKey(IFD.NEW_SUBFILE_TYPE)) {
                    if (ifd.getIFDIntValue(IFD.NEW_SUBFILE_TYPE)==0) {
                        ifd.put(TEMP, i);
                        neolist.add(ifd);
                    }
                }
            }
        } else if (reader instanceof TiffReader r) {
            list = r.getIFDs();
            neolist = new ArrayList<>(list.size());
            for(int i=0; i<list.size();i++) {
                IFD ifd = list.get(i);
                ifd.put(TEMP, i);
                neolist.add(ifd);
            }
        } else if (reader instanceof NDPIReader r) {
            list = r.getIFDs();
            neolist = new ArrayList<>(list.size());
            for(int i=0; i<list.size();i++) {
                IFD ifd = list.get(i);
                ifd.put(TEMP, i);
                neolist.add(ifd);
            }
        } else {
            throw new Error("No support for "+reader.getClass().toGenericString());
        }
        index = new int[neolist.size()];
        width = new int[neolist.size()];
        height = new int[neolist.size()];
        ratio = new float[neolist.size()];
        for (int i=0; i<neolist.size();i++) {
            IFD ifd = neolist.get(i);
            index[i] = ifd.getIFDIntValue(TEMP);
            width[i] = ifd.getIFDIntValue(IFD.IMAGE_WIDTH);
            height[i] = ifd.getIFDIntValue(IFD.IMAGE_LENGTH);
            ratio[i] = (i==0) ? 1 : (float) width[0]/ (float) width[i];
        }
    }
    
    public int getBest(float r) {
        int best = neolist.size()-1;
        float rr = 0.8f*ratio[best];
        while ((r<rr)&&(best>0)) {
            best--;
            rr = 0.8f*ratio[best];
        }
        return index[best];
    }
}
