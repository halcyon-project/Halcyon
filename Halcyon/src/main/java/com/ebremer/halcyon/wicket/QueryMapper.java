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

/**
 *
 * @author erich
 */
public class QueryMapper {
    
    private final HashMap<String, Node> variables;
    private final Query q;

    public QueryMapper(Query q) {
        this.q = q;
        this.variables = new HashMap<>();
        Process(q);
    }
    
    private void Process(Query q) {
        System.out.println("Process : "+q.toString());
        Element e = q.getQueryPattern();
        System.out.println("QP : "+e.toString());
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
            System.out.println("Process Element - Unknown : "+e.getClass().getCanonicalName());
        }        
    }
    
    private void Process(ElementGroup eg) {
         System.out.println("EG : "+eg.toString());
        List<Element> es = eg.getElements();
        Iterator<Element> i = es.iterator();
        while (i.hasNext()) {
            Process(i.next());
        }        
    }
    
    private void Process(Node n) {
       
    }
    
    private void Process(TriplePath tp) {
        System.out.println("TP > "+tp.toString());
        System.out.println("Subject   : "+ tp.getSubject());
        System.out.println("Predicate : "+ tp.getPredicate());
        System.out.println("Object    : "+ tp.getObject());
        System.out.println("Path      : "+ tp.getPath());
        Node s = tp.getSubject();
        Node o = tp.getObject();
        System.out.println("SU "+s.isURI());
        System.out.println("SV "+s.isVariable());
        System.out.println("OU "+o.isURI());
        System.out.println("OL "+o.isLiteral());
        System.out.println("OV "+o.isVariable());
        
        //variables.put(s.getName(), s);
        //variables.put(o.getName(), o).
        if (tp.isTriple()) {
            Node p = tp.getPredicate();
            System.out.println("PU "+p.isURI());    
            System.out.println("PV "+p.isVariable());
        }
    }
    
    private void Process(ElementPathBlock epb) {
        System.out.println("EPB : "+epb.toString());
        Iterator<TriplePath> i = epb.patternElts();
        while (i.hasNext()) {
            Process(i.next());
        }
    }

    private void Process(ElementNamedGraph eng) {
        System.out.println("ENG : "+eng.toString());
        System.out.println("NG : "+eng.getGraphNameNode().getName());
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
