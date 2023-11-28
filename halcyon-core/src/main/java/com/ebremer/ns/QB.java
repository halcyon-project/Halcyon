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

public class QB {

/**
 *  The RDF Data Cube Vocabulary
 *  <p>
 *	See <a href="https://www.w3.org/TR/vocab-data-cube/">The RDF Data Cube Vocabulary</a>.
 *  <p>
 *  <a href="https://www.w3.org/TR/vocab-data-cube/>Base URI and namepace</a>.
 */
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://purl.org/linked-data/cube#";

    public static final Resource Attachable = m.createResource(NS+"Attachable");
    public static final Resource AttributeProperty = m.createResource(NS+"AttributeProperty");
    public static final Resource CodedProperty = m.createResource(NS+"CodedProperty");
    public static final Resource ComponentProperty = m.createResource(NS+"ComponentProperty");
    public static final Resource ComponentSet = m.createResource(NS+"ComponentSet");
    public static final Resource ComponentSpecification = m.createResource(NS+"ComponentSpecification");
    public static final Resource DataSet = m.createResource(NS+"DataSet");
    public static final Resource DataStructureDefinition = m.createResource(NS+"DataStructureDefinition");
    public static final Resource DimensionProperty = m.createResource(NS+"DimensionProperty");
    public static final Resource HierarchicalCodeList = m.createResource(NS+"HierarchicalCodeList");
    public static final Resource MeasureProperty = m.createResource(NS+"MeasureProperty");
    public static final Resource Observation = m.createResource(NS+"Observation");
    public static final Resource ObservationGroup = m.createResource(NS+"ObservationGroup");
    public static final Resource Slice = m.createResource(NS+"Slice");
    public static final Resource SliceKey = m.createResource(NS+"SliceKey");
    public static final Property attribute = m.createProperty(NS+"attribute");
    public static final Property codeList = m.createProperty(NS+"codeList");
    public static final Property component = m.createProperty(NS+"component");
    public static final Property componentAttachment = m.createProperty(NS+"componentAttachment");
    public static final Property componentProperty = m.createProperty(NS+"componentProperty");
    public static final Property componentRequired = m.createProperty(NS+"componentRequired");
    public static final Property concept = m.createProperty(NS+"concept");
    public static final Property dataSet = m.createProperty(NS+"dataSet");
    public static final Property dimension = m.createProperty(NS+"dimension");
    public static final Property hierarchyRoot = m.createProperty(NS+"hierarchyRoot");
    public static final Property measure = m.createProperty(NS+"measure");
    public static final Property measureDimension = m.createProperty(NS+"measureDimension");
    public static final Property measureType = m.createProperty(NS+"measureType");
    public static final Property observation = m.createProperty(NS+"observation");
    public static final Property observationGroup = m.createProperty(NS+"observationGroup");
    public static final Property order = m.createProperty(NS+"order");
    public static final Property parentChildProperty = m.createProperty(NS+"parentChildProperty");
    public static final Property slice = m.createProperty(NS+"slice");
    public static final Property sliceKey = m.createProperty(NS+"sliceKey");
    public static final Property sliceStructure = m.createProperty(NS+"sliceStructure");
    public static final Property structure = m.createProperty(NS+"structure");
}
