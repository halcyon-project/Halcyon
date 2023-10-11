/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ebremer.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class SD {

/**
 *  SPARQL 1.1 Service Description
 *  <p>
 *	See <a href="https://www.w3.org/TR/sparql11-service-description/">SPARQL 1.1 Service Description</a>.
 *  <p>
 *  <a href="http://www.w3.org/ns/sparql-service-description#>Base URI and namepace</a>.
 */
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://www.w3.org/ns/sparql-service-description#";

    public static final Resource EntailmentRegime = m.createResource(NS+"EntailmentRegime");
    public static final Resource Aggregate = m.createResource(NS+"Aggregate");
    public static final Resource EntailmentProfile = m.createResource(NS+"EntailmentProfile");
    public static final Resource GraphCollection = m.createResource(NS+"GraphCollection");
    public static final Resource Dataset = m.createResource(NS+"Dataset");
    public static final Resource Graph = m.createResource(NS+"Graph");
    public static final Resource Feature = m.createResource(NS+"Feature");
    public static final Resource NamedGraph = m.createResource(NS+"NamedGraph");
    public static final Resource Language = m.createResource(NS+"Language");
    public static final Resource Function = m.createResource(NS+"Function");
    public static final Resource Service = m.createResource(NS+"Service");
    
    public static final Property defaultEntailmentRegime = m.createProperty(NS+"defaultEntailmentRegime");
    public static final Property supportedLanguage = m.createProperty(NS+"supportedLanguage");
    public static final Property availableGraphs = m.createProperty(NS+"availableGraphs");
    public static final Property defaultDataset = m.createProperty(NS+"defaultDataset");
    public static final Property propertyFeature = m.createProperty(NS+"propertyFeature");
    public static final Property extensionFunction = m.createProperty(NS+"extensionFunction");
    public static final Property name = m.createProperty(NS+"name");
    public static final Property feature = m.createProperty(NS+"feature");
    public static final Property entailmentRegime = m.createProperty(NS+"entailmentRegime");
    public static final Property defaultGraph = m.createProperty(NS+"defaultGraph");
    public static final Property namedGraph = m.createProperty(NS+"namedGraph");
    public static final Property extensionAggregate = m.createProperty(NS+"extensionAggregate");
    public static final Property inputFormat = m.createProperty(NS+"inputFormat");
    public static final Property languageExtension = m.createProperty(NS+"languageExtension");
    public static final Property supportedEntailmentProfile = m.createProperty(NS+"supportedEntailmentProfile");
    public static final Property defaultSupportedEntailmentProfile = m.createProperty(NS+"defaultSupportedEntailmentProfile");
    public static final Property graph = m.createProperty(NS+"graph");
    public static final Property resultFormat = m.createProperty(NS+"resultFormat");
    public static final Property endpoint = m.createProperty(NS+"endpoint");
}
