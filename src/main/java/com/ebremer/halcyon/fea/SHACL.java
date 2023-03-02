/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.fea;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 *
 * @author erich
 */
public class SHACL {
    private static String shapes1 =
            """
            @prefix dash: <http://datashapes.org/dash#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                                     @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                                     @prefix so: <https://schema.org/> .
                                     @prefix sh: <http://www.w3.org/ns/shacl#> .
                                     @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                                     @prefix oa: <http://www.w3.org/ns/oa#> .
                                     @prefix hal: <https://www.ebremer.com/halcyon/ns/> .
                                     
                                     #<urn:hal:HalcyonDataFileShape>
                                     #    a sh:NodeShape;
                                     #    sh:targetClass hal:DataFile;
                                     #    sh:property [
                                     #        sh:path so:hasPart;
                                     #        sh:node <urn:hal:CreateActionShape>;
                                     #        sh:minCount 1;
                                     #        sh:maxCount 1
                                     #    ] .    
                                     
                                     <urn:hal:DataSetShape>
                                         a sh:NodeShape;
                                         sh:targetClass so:Dataset;
                                         sh:qualifiedMinCount 1;
                                         sh:qualifiedMaxCount 1;
                                         sh:property [
                                            sh:name "ROCRATE Name" ;
                                            sh:path so:name;
                                            sh:datatype xsd:string;
                                            sh:minLength 1;
                                            sh:maxLength 200;
                                            sh:minCount 1;
                                            sh:maxCount 1
                                         ];
                                         sh:property [
                                             sh:path so:creator;
                                             sh:nodeKind sh:IRI;
                                             sh:minCount 1
                                         ];
#                                         sh:property [
#                                            sh:path so:publisher;
#                                            sh:nodeKind sh:IRI;
#                                            sh:minCount 1
#                                         ];
                                         sh:property [
                                             sh:path so:description;
                                             sh:datatype xsd:string;
                                             sh:maxCount 1
                                         ] .
                                     
                                     <urn:hal:CreateActionShape>
                                         a sh:NodeShape;
                                         sh:targetClass so:CreateAction;
                                         sh:property [
                                             sh:path so:name;
                                             sh:minLength 1;
                                             sh:maxLength 200;
                                             sh:datatype xsd:string;
                                             sh:minCount 1;
                                             sh:maxCount 1
                                         ];
                                         sh:property [
                                             sh:path so:instrument;
                                             sh:nodeKind sh:IRI;
                                             sh:minCount 1;
                                             sh:maxCount 1
                                         ];
                                         sh:property [
                                             sh:path so:result;
                                             sh:minCount 1;
                                             sh:maxCount 1;
                                             sh:node <urn:hal:DataSetShape>
                                         ];
                                         sh:property [
                                             sh:path so:description;
                                             sh:datatype xsd:string;
                                             sh:maxCount 1
                                         ] .
                                     
                                     <urn:hal:AnnotationShape>
                                         a sh:NodeShape;
                                         sh:targetClass oa:Annotation;
                                         sh:property [
                                             sh:path oa:hasBody;
                                             sh:minCount 1;
                                             sh:maxCount 1;
                                             sh:node <urn:hal:AnnotationBodyShape>
                                         ];
                                         sh:property [
                                             sh:path oa:hasSelector;
                                             sh:minCount 1;
                                             sh:maxCount 1;
                                             sh:node <urn:hal:AnnotationSelectorShape>
                                         ] .
                                     
                                     <urn:hal:AnnotationSelectorShape>
                                         a sh:NodeShape;
                                         sh:targetClass oa:FragmentSelector;
                                         sh:property [
                                             sh:path rdf:value;
                                             sh:datatype xsd:string;
                                             sh:minCount 1;
                                             sh:maxCount 1
                                         ];
                                         sh:property [
                                             sh:path oa:hasSource;
                                             sh:nodeKind sh:IRI;
                                             sh:minCount 1;
                                             sh:maxCount 1
                                         ] .
                                     
                                     <urn:hal:AnnotationBodyShape>
                                         a sh:NodeShape;
                                         sh:targetClass hal:ProbabilityBody;
                                         sh:property [
                                             sh:path hal:assertedClass;
                                             sh:nodeKind sh:IRI;
                                             sh:minCount 1;
                                             sh:maxCount 1
                                         ]; 
                                         sh:property [
                                             sh:path hal:hasCertainty;
                                             sh:datatype xsd:float;
                                             sh:minCount 1;
                                             sh:maxCount 1
                                         ] .                         
            """;
    
    public static Graph getGraph11() {
        Model m = ModelFactory.createDefaultModel();
        InputStream ips = new ByteArrayInputStream(shapes1.getBytes());
        RDFDataMgr.read(m, ips, Lang.TURTLE);
        return m.getGraph();
    }
}
