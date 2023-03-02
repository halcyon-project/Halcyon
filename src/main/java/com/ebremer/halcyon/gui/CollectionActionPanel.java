/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.gui;

import com.ebremer.ethereal.Solution;
import com.ebremer.halcyon.datum.SecuredDatasetGraph;
import com.ebremer.halcyon.wicket.DatabaseLocator;
import com.ebremer.ns.HAL;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.WAC;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 *
 * @author erich
 */
public class CollectionActionPanel extends Panel {
    
        public CollectionActionPanel(String id, IModel<Solution> model, String item) {
            super(id, model);
            Solution s = model.getObject();
            int numRead = (int) s.getMap().get("numRead").getLiteralValue();
            int numWrite = (int) s.getMap().get("numWrite").getLiteralValue();
            String agent = s.getMap().get("s").getURI();
            Link ReadLink = new Link<Void>("Add") {
                @Override
                public void onClick() {
                    if (numRead==0) {
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            insert data {graph ?SecurityGraph {[ wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Read]}} 
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        ds.begin(ReadWrite.WRITE);
                        UpdateAction.parseExecute(pss.toString(), ds);
                        ds.commit();
                        ds.end();
                    } else {
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            delete where {graph ?SecurityGraph {?aclRead wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Read}}
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        ds.begin(ReadWrite.WRITE);
                        UpdateAction.parseExecute(pss.toString(), ds);
                        ds.commit();
                        ds.end();
                    }
                  //  Dataset ds = DatabaseLocator.getDatabase().getDataset();
                  //  ds.begin(ReadWrite.READ);
                   // org.apache.jena.rdf.model.Model sec = ds.getNamedModel(HAL.SecurityGraph.getURI());
                   // ds.end();
                }
            };
            if (numRead>0) {
                ReadLink.setBody(Model.of("Remove Read"));
            } else {
                ReadLink.setBody(Model.of("Add Read"));
            }
            add(ReadLink);
            Link WriteLink = new Link<Void>("Delete") {
                @Override
                public void onClick() {
                    if (numWrite==0) {
                        System.out.println("Add Delete Access to : "+agent);
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            insert data {graph ?SecurityGraph {[ wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Write]}} 
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        ds.begin(ReadWrite.WRITE);
                        UpdateAction.parseExecute(pss.toString(), ds);
                        ds.commit();
                        ds.end();
                       // System.out.println(pss.toString());
                    } else {
                        System.out.println("Remove Delete Access to : "+agent);
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            delete where {graph ?SecurityGraph {?aclWrite wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Write}}
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        ds.begin(ReadWrite.WRITE);
                        UpdateAction.parseExecute(pss.toString(), ds);
                        ds.commit();
                        ds.end();
                       // System.out.println(pss.toString());
                    }
                    Dataset ds = DatabaseLocator.getDatabase().getDataset();
                    ds.begin(ReadWrite.READ);
                    org.apache.jena.rdf.model.Model sec = ds.getNamedModel(HAL.SecurityGraph.getURI());
                    //RDFDataMgr.write(System.out, sec, Lang.TURTLE);
                    ds.end();
                }
            };
            add(WriteLink);
            if (numWrite>0) {
                WriteLink.setBody(Model.of("Remove Write"));
            } else {
                WriteLink.setBody(Model.of("Add Write"));
            }
        }
    }