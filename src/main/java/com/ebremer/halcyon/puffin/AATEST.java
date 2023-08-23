package com.ebremer.halcyon.puffin;

import org.apache.jena.arq.querybuilder.ExprFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;

/**
 *
 * @author erich
 */
public class AATEST {
    
    public static void main(String[] args) {
        Query query = QueryFactory.create();
        query.setQuerySelectType();
        Var s = Var.alloc("s");
        Var p = Var.alloc("p");
        Var o = Var.alloc("o");
        ElementTriplesBlock block = new ElementTriplesBlock();
        block.addTriple(Triple.create(s, p, o));
        query.addResultVar(p);
        query.addResultVar(o);
        query.addResultVar(s);

        query.setDistinct(false);
        query.setQueryPattern(block);
        System.out.println("===================================================");
        System.out.println(query.serialize(Syntax.syntaxSPARQL));
        ExprFactory ha = new ExprFactory();
        
    }
    
}
