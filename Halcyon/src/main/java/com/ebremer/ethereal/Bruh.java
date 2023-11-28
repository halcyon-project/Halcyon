package com.ebremer.ethereal;

import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.arq.querybuilder.WhereBuilder;
import org.apache.jena.arq.querybuilder.handlers.SelectHandler;
import org.apache.jena.arq.querybuilder.handlers.ValuesHandler;
import org.apache.jena.arq.querybuilder.handlers.WhereHandler;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.aggregate.Aggregator;
import org.apache.jena.sparql.expr.aggregate.AggregatorFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class Bruh {
    
    public static void main(String[] args) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?s ?dateRegistered ?width ?height ?md5
            where {
                graph ?car {?s so:isPartOf ?collection}
                graph ?s {?s a so:ImageObject;
                            owl:sameAs ?md5;
                            exif:width ?width;
                            exif:height ?height
                }
            }
            """
        );
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        Query q = QueryFactory.create(pss.toString());
        WhereHandler wh = new WhereHandler(q);
        TriplePath tp = new TriplePath(new Triple(NodeFactory.createVariable("ca"), SchemaDO.object.asNode(), NodeFactory.createVariable("md5")));
        wh.addGraph(NodeFactory.createVariable("roc"), tp);
        ValuesHandler vh = new ValuesHandler(q);
        ArrayList<Node> features = new ArrayList<>();
        features.add(NodeFactory.createURI("http://www.ebremer.com/wow"));
        vh.addValueVar(Var.alloc("ca"), features);
        vh.build();
        wh.addWhere(vh);
        System.out.println(q.toString());
        
        q = QueryFactory.create(q.toString());
        Aggregator agg = AggregatorFactory.createCount(false);
        Expr exprAgg = q.allocAggregate(agg) ;

        boolean dist = q.isDistinct();
        q.setDistinct(false);
        List<Var> list = new LinkedList<>();
        list.addAll(q.getProject().getVars());
        q.getProject().clear();
        q.addResultVar("count", exprAgg);
        
        System.out.println("BOOMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n"+q.toString());
        q.getProjectVars().clear();
        q.addProjectVars(list);
        q.setDistinct(dist);
        
        System.out.println("BOOM!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"+q.toString());
    }
    
}
