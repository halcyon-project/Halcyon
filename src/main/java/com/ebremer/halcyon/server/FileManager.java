package com.ebremer.halcyon.server;

import com.ebremer.halcyon.lib.HalcyonSettings;
import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.filesystem.DirectoryProcessor;
import com.ebremer.halcyon.filesystem.StorageLocation;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;

/**
 *
 * @author erich
 */
public final class FileManager implements Service {
    private static HalcyonSettings hs = null;
    private Timer timer;
    private TimerTask task;
    
    public FileManager() {
        System.out.println("Starting FileManager...");
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
                    dp.Traverse(p);
                }
                ValidateData();
                resume();
            }
        };
        long delay = 1000L * 10L;
        this.timer.schedule(task, delay);
    }
 
    public void ListStorageAreas() {
        ArrayList<StorageLocation> list = hs.getStorageLocations();
        Iterator<StorageLocation> i = list.iterator();
        while (i.hasNext()) {
            Path p = i.next().path;
        }
    }
    
    public void ValidateData() {
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        QueryExecution qe = QueryExecutionFactory.create("select distinct ?g where {graph ?g {?g ?p ?o}}", ds);
        ResultSet results = qe.execSelect().materialise();
        ds.end();
        results.forEachRemaining(qs->{
            String r = qs.get("g").toString();
            if (r.startsWith("file:/")) {
                try {
                    URI uri = new URI(r);
                    Path path = Path.of(uri);
                    if (!path.toFile().exists()) {
                        UpdateRequest request = UpdateFactory.create();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("delete where {graph ?g {?s ?p ?o}}");
                        pss.setIri("g", r);
                        request.add(pss.toString());
                        ds.begin(ReadWrite.WRITE);
                        UpdateAction.execute(request, ds);
                        ds.commit();
                        ds.end();
                        System.out.println("DELETED : "+r);
                    }
                } catch (URISyntaxException ex) {
                    System.out.println("NG --> "+r);
                    Logger.getLogger(FileManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    @Override
    public String getName() {
        return "FileManager";
    }
}