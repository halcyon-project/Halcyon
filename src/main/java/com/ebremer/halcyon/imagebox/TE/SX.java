package com.ebremer.halcyon.imagebox.TE;

import java.net.URI;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * @author erich
 */
public class SX {
    private final LinkedBlockingDeque<Future<Tile>> buffer;
    private final LinkedList<TileRequest> list;
    private int numtiles;
    
    public SX(URI uri, Rectangle tileSize, Rectangle preferredsize) throws Exception {
        buffer = new LinkedBlockingDeque<>();
        list = new LinkedList<>();
        ImageReader reader = ImageReaderPool.getPool().borrowObject(uri);
        ImageMeta meta = reader.getMeta();
        int numTilesX = (int) Math.round((double) meta.getWidth()/ (double) tileSize.width());
        int numTilesY = (int) Math.round((double) meta.getHeight()/ (double) tileSize.height());
        try {
            final TileRequestEngine tre = new TileRequestEngine(uri);
            Tile tile = tre.getTile(new ImageRegion(0,0,meta.getWidth(),meta.getHeight()), new Rectangle(meta.getWidth()>>6,0), false);
            boolean[][] background = BackgroundDetector.getBackgroundMask(tile.getBufferedImage(),numTilesX, numTilesY);
            for (int i = 0; i < numTilesX; i++) {
                for (int j = 0; j < numTilesY; j++) {
                    if (!background[i][j]) {
                        int TL = i*tileSize.width();
                        int BL = j*tileSize.height();
                        int TR = TL+tileSize.width();
                        int BR = BL+tileSize.height();
                        TR = TR<meta.getWidth()?tileSize.width():TR-meta.getWidth();
                        BR = BR<meta.getHeight()?tileSize.height():BR-meta.getHeight();
                        list.add(TileRequest.genTileRequest(uri, new ImageRegion(TL,BL,TR,BR), preferredsize, false));
                    }
                }
            }
            numtiles = list.size();
            System.out.println("# of tiles --> "+numtiles);
            list.forEach(tr->{
                buffer.add(tre.getFutureTile(tr));
            });
            list.clear();
        } catch (Exception ex) {
            Logger.getLogger(SX.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private class TileIterator implements Iterator<Tile> {

        @Override
        public boolean hasNext() {
            return !buffer.isEmpty();
        }

        @Override
        public Tile next() {
            if ((buffer.size()%2500)==0) {
                DecimalFormat df = new DecimalFormat("#.##");
                System.out.println(buffer.size()+"  "+df.format(100d*(1.0d-((double) buffer.size())/((double) numtiles))));
            }
            boolean found = false;
            while (!found) {
                for (Future<Tile> ff : buffer) {
                    if (ff.isDone()) {
                        buffer.remove(ff);
                        try {
                            return ff.get();
                        } catch (InterruptedException ex) {
                            Logger.getLogger(SX.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ExecutionException ex) {
                            Logger.getLogger(SX.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
            return null;
        }
    }

    public Stream<Tile> getTiles() throws Exception {
        Iterator<Tile> iterator = new TileIterator();
        return StreamSupport.stream( Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), true );
    }   
}
