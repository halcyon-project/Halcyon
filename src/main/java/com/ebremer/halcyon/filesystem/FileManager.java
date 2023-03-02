package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.halcyon.datum.DataCore;
import static com.ebremer.halcyon.filesystem.DirectoryProcessor.GetExtensions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.jena.query.Dataset;

/**
 *
 * @author erich
 */
public final class FileManager {
    private static FileManager fm = null;
    private static HalcyonSettings hs = null;
    private Timer timer;
    private TimerTask task;
    
    private FileManager() {
        hs = HalcyonSettings.getSettings();
        resume();
    }
    
    public void pause() {
        this.timer.cancel();
    }
    
    public void resume() {
        this.timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                pause();
                Dataset ds = DataCore.getInstance().getDataset();
                DirectoryProcessor dp = new DirectoryProcessor(ds);
                ArrayList<StorageLocation> list = hs.getStorageLocations();
                Iterator<StorageLocation> i = list.iterator();
                while (i.hasNext()) {
                    Path p = i.next().path;
                    dp.Traverse(p, GetExtensions(DirectoryProcessor.ZIP), DirectoryProcessor.ZIP);
                    dp.Traverse(p, GetExtensions(DirectoryProcessor.SVS), DirectoryProcessor.SVS);
                    dp.Traverse(p, GetExtensions(DirectoryProcessor.TIF), DirectoryProcessor.TIF);
                    dp.Traverse(p, GetExtensions(DirectoryProcessor.NDPI), DirectoryProcessor.NDPI);
                }
                resume();
            }
        };
        long delay = 1000L * 10L;
        this.timer.schedule(task, delay);
    }

    public synchronized static FileManager getInstance() {
        if (fm==null) {
            fm = new FileManager();
        }
        return fm;
    }
 
    public void ListStorageAreas() {
        ArrayList<StorageLocation> list = hs.getStorageLocations();
        Iterator<StorageLocation> i = list.iterator();
        while (i.hasNext()) {
            Path p = i.next().path;
        }
    }
}