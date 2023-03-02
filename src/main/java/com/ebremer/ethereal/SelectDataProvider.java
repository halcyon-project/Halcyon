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
        System.out.println("SelectDataProvider "+s);
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
        System.out.println("\nsetSPARQL =====================================================================\n"+sparql+"\n===============================================================================\n");
        activesparql = sparql;
        updateCount();
    }
    
    public void setQuery(Query q) {
        activesparql = q.toString();
        System.out.println("\nsetQuery =====================================================================\n"+activesparql+"\n===============================================================================\n");
        updateCount();
    }

    private void updateCount() {
        StopWatch w = new StopWatch(true);
        Query q = getQuery();
        Dataset ds = dds.load();
        ds.begin(ReadWrite.READ);  // threw a "Caused by: java.lang.Error: Maximum lock count exceeded"
        QueryExecution qe = QueryExecutionFactory.create(q,ds);
        ResultSet rs = qe.execSelect().materialise();
        ds.end();
        size = 0;
        while (rs.hasNext()) {
            rs.next();
            size++;
        }
        w.getTime("updateCount() "+size);
    }    

    @Override
    public Iterator<Solution> iterator(long first, long count) {
        //StopWatch w = new StopWatch(true);
        ArrayList<Solution> list = new ArrayList<>((int) count);
        Query q = getQuery();
        q.setLimit(count);
        q.setOffset(first);
        Dataset ds = dds.load();
        ds.begin();
        ResultSet resultset = QueryExecutionFactory.create(q, ds).execSelect().materialise();
        ds.end();
        resultset.forEachRemaining(qs->{
            list.add(new Solution(qs));       
        });
        //w.getTime("Iterator<Solution> iterator(long first, long count) "+first+" "+count);
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


        //q.setDistinct(false);
        //q.getProject().clear();
        //Aggregator agg = AggregatorFactory.createCount(false);
        //Expr exprAgg = q.allocAggregate(agg) ;
        //q.getProjectVars().clear();
        //q.addResultVar("count", exprAgg);
        //System.out.println("BOOM\n"+q.toString());