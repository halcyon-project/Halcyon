package com.ebremer.ethereal;

import com.ebremer.halcyon.utils.StopWatch;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class SelectDataProvider extends SortableDataProvider<Solution, String> {
    private long size;
    private final String sparql;
    private String activesparql;
    private final DetachableDataset dds;
   
    public SelectDataProvider(Dataset ds, String s) {
        this.dds = new DetachableDataset(ds);
        this.sparql = s;
    }
    
    public Query getQuery() {
        return QueryFactory.create(activesparql);
    }
    
    public Dataset getDS() {
        return dds.getObject();
    }    
    
    public ParameterizedSparqlString getPSS() {
        return new ParameterizedSparqlString(sparql);
    }
    
    public void SetSPARQL(String sparql) {
        activesparql = sparql;
        updateCount();
    }
    
    public void setQuery(Query q) {
        activesparql = q.toString();
        updateCount();
    }

    private void updateCount() {
        StopWatch w = new StopWatch();
        Query q = getQuery();
        Dataset ds = dds.load();
        QueryExecution qe = QueryExecutionFactory.create(q,ds);
        ds.begin(ReadWrite.READ);
        ResultSet rs = qe.execSelect();
        size = 0;
        while (rs.hasNext()) {
            rs.next();
            size++;
        }
        ds.end();
        w.getTime("updateCount() "+size);
    }    

    @Override
    public Iterator<Solution> iterator(long first, long count) {
        ArrayList<Solution> list = new ArrayList<>((int) count);
        Query q = getQuery();
        q.setLimit(count);
        q.setOffset(first);
        Dataset ds = dds.load();
        //ds.begin();
        ResultSet resultset = QueryExecutionFactory.create(q, ds).execSelect().materialise();
        //ds.end();
        resultset.forEachRemaining(qs->{
            list.add(new Solution(qs));       
        });
        return list.iterator();
    }

    @Override
    public long size() {
        return size;
    }
    
    @Override
    public IModel<Solution> model(Solution object) {
        return new SolutionModel(new SolutionLoadableDetachableModel(object));
    }
}
