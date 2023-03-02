package com.ebremer.halcyon.datum;

import com.ebremer.ns.HAL;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

/**
 *
 * @author erich
 */
public class RDFDump {
    
    public static void main(String[] args) {
        loci.common.DebugTools.setRootLevel("WARN");
        DataCore datacore = DataCore.getInstance();
        Dataset ds = datacore.getDataset();
        ds.begin(ReadWrite.READ);

        RDFDataMgr.write(System.out, ds, RDFFormat.TRIG_PRETTY);
      /*  
        Model secm = ModelFactory.createDefaultModel();
        secm.add(ds.getNamedModel(HAL.SecurityGraph.getURI()));
        secm.add(ds.getNamedModel(HAL.CollectionsAndResources.getURI()));
        secm.add(ds.getNamedModel(HAL.GroupsAndUsers.getURI()));      
        
        ds.end();
        ds.close();
        datacore.shutdown();
        System.out.println("===========================================");
        //RDFDataMgr.write(System.out,secm, Lang.TURTLE);
        */
    }
}


/*

        
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?CollectionName where {
                graph ?s {?s a so:Collection; rdfs:label ?CollectionName}
                filter (strstarts(?CollectionName, "B"))
            } order by desc(?CollectionName)
        """);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        SPARQLParser k = SPARQLParser.createParser(Syntax.syntaxSPARQL_11);
        Query ha = new Query();
        k.parse(ha, pss.toString());
        ElementGroup x = (ElementGroup) ha.getQueryPattern();
        List<Element> m = x.getElements();
        Iterator<Element> n = m.iterator();
        ElementFilter ef = null;
        while (n.hasNext()) {
            Element e = n.next();
            if (e instanceof ElementNamedGraph) {
                ElementNamedGraph eng = (ElementNamedGraph) e;
                
            } else if (e instanceof ElementFilter) {
                ef = (ElementFilter) e;
                System.out.println("FILTER : "+ef.toString());
                
            } else {
                System.out.println("ELEMENT : "+e.getClass().toString());
            }
        }
        if (ef!=null) {
            m.remove(ef);
            //SelectBuilder sub = new SelectBuilder();
            //sub.addFilter("wow");
            WhereHandler wh = new WhereHandler(ha);
            try {
                wh.addFilter("strstarts(?CollectionName, \"S\")");
                //Expr ex = new Expr();
                //ElementFilter ff = new ElementFilter();
            } catch (ParseException ex) {
                Logger.getLogger(RDFDump.class.getName()).log(Level.SEVERE, null, ex);
            }
           
        }
       
        System.out.println(x.getClass().toString());
        List<SortCondition> list = ha.getOrderBy();
        Iterator<SortCondition> i = list.iterator();
        while (i.hasNext()) {
            SortCondition sort = i.next();
            System.out.println("SORT CONDITION : "+sort.toString());
        }
        list.remove(0);
        SortCondition sc = new SortCondition(Var.alloc("CollectionName"), Query.ORDER_ASCENDING);
        list.add(sc);
        list = ha.getOrderBy();
        i = list.iterator();
        while (i.hasNext()) {
            SortCondition sort = i.next();
            System.out.println("SORT CONDITION : "+sort.toString());
        }
        QueryExecution qe = QueryExecutionFactory.create(ha,ds);
        ResultSet resultset = qe.execSelect();
        while (resultset.hasNext()) {
            QuerySolution qs = resultset.next();
            System.out.println(qs.toString());
                    
        }
        */
        //System.out.print("EXISTS : "+ds.containsNamedModel("urn:uuid:ae26e080-4f32-4faf-bdf0-599607377a0c"));