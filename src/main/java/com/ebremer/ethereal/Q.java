/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.arq.querybuilder.handlers.WhereHandler;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.lang.sparql_11.ParseException;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class Q {
    
    public Q() {
        
    }
    
    public static void removeAllFilters(Query query) {
        ElementGroup x = (ElementGroup) query.getQueryPattern();
        List<Element> m = x.getElements();
        Iterator<Element> n = m.iterator();
        List<Element> delete = new ArrayList<>();
        while (n.hasNext()) {
            Element e = n.next();
            if (e instanceof ElementFilter) {
                delete.add(e);
            }
        }
        m.removeAll(delete);
    }
    
    /*
    public static void getElementFilter(Query query) {
        ElementGroup x = (ElementGroup) query.getQueryPattern();
        List<Element> m = x.getElements();
        Iterator<Element> n = m.iterator();
        ElementFilter ef;
        while (n.hasNext()) {
            Element e = n.next();
            if (e instanceof ElementFilter elementFilter) {
                ef = elementFilter;
                //System.out.println("FILTER : "+ef.toString());
            } //else {
                //System.out.println(e.getClass().toString());
                //System.out.println("OTHER  : "+e.toString());
            //}
        }
    }
    */
    public static void main(String[] args) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select distinct ?s ?dateRegistered ?width ?height ?alias
            where {graph ?s {?s owl:sameAs ?alias;
                                a so:ImageObject;
                                hal:dateRegistered ?dateRegistered;
                                exif:width ?width;
                                exif:height ?height
                            }
                            optional {graph ?s {?s so:isPartOf ?collection}}
                            filter(!bound(?collection))
                   }
        """);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("exif", EXIF.NS);
        Query ha = QueryFactory.create(pss.toString());
        WhereHandler wh = new WhereHandler(ha);
        try {
            wh.addFilter("filter(!bound(?collection))");
        } catch (ParseException ex) {
            Logger.getLogger(Q.class.getName()).log(Level.SEVERE, null, ex);
        }
        //Q.getElementFilter(ha);
    }
}
