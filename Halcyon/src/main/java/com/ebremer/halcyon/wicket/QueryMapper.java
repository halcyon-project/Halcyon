package com.ebremer.halcyon.wicket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.query.Syntax;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.lang.SPARQLParser;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementNamedGraph;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class QueryMapper {
    private static final Logger logger = LoggerFactory.getLogger(QueryMapper.class);
    
    private final HashMap<String, Node> variables;
    private final Query q;

    public QueryMapper(Query q) {
        this.q = q;
        this.variables = new HashMap<>();
        Process(q);
    }
    
    private void Process(Query q) {
        logger.debug("Process : {}", q.toString());
        Element e = q.getQueryPattern();
        logger.debug("QP : {}", e.toString());
        Process(e);
    }
    
    private void Process(Element e) {
        if (e instanceof ElementGroup) {
            Process((ElementGroup) e);
        } else if (e instanceof ElementPathBlock) {
            Process((ElementPathBlock) e);
        } else if (e instanceof ElementNamedGraph) {
            Process((ElementNamedGraph) e);
        } else {
            logger.debug("Process Element - Unknown : {}", e.getClass().getCanonicalName());
        }        
    }
    
    private void Process(ElementGroup eg) {
         logger.debug("EG : {}", eg.toString());
        List<Element> es = eg.getElements();
        Iterator<Element> i = es.iterator();
        while (i.hasNext()) {
            Process(i.next());
        }        
    }
    
    private void Process(Node n) {
       
    }
    
    private void Process(TriplePath tp) {
        logger.debug("TP > {}", tp.toString());
        logger.debug("Subject   : {}", tp.getSubject());
        logger.debug("Predicate : {}", tp.getPredicate());
        logger.debug("Object    : {}", tp.getObject());
        logger.debug("Path      : {}", tp.getPath());
        Node s = tp.getSubject();
        Node o = tp.getObject();
        logger.debug("SU {}", s.isURI());
        logger.debug("SV {}", s.isVariable());
        logger.debug("OU {}", o.isURI());
        logger.debug("OL {}", o.isLiteral());
        logger.debug("OV {}", o.isVariable());
        
        //variables.put(s.getName(), s);
        //variables.put(o.getName(), o).
        if (tp.isTriple()) {
            Node p = tp.getPredicate();
            logger.debug("PU {}", p.isURI());    
            logger.debug("PV {}", p.isVariable());
        }
    }
    
    private void Process(ElementPathBlock epb) {
        logger.debug("EPB : {}", epb.toString());
        Iterator<TriplePath> i = epb.patternElts();
        while (i.hasNext()) {
            Process(i.next());
        }
    }

    private void Process(ElementNamedGraph eng) {
        logger.debug("ENG : {}", eng.toString());
        logger.debug("NG : {}", eng.getGraphNameNode().getName());
        Process(eng.getElement());
    }
    
    public static void main(String[] args) {
        SPARQLParser k = SPARQLParser.createParser(Syntax.syntaxSPARQL_11);
        Query haha = new Query();
        k.parse(haha, "prefix : <https://halcyon.is/ns/> select ?g ?s ?p ?o ?pp ?oo where {?s ?p ?o . graph ?g {?s ?p ?o . ?o ?pp ?oo; ?boo 'Bremer'; :alpha/:beta/:gamma ?wow}}");
        QueryMapper qm = new QueryMapper(haha);
        Iterator<Var> i = haha.getProjectVars().iterator();
        while (i.hasNext()) {
            Var ha = i.next();
//            System.out.println(ha.getName()+" "+ha.getVarName()+" "+ha.asNode().isVariable());
        }
    }
}
