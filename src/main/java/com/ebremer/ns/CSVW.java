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

public class CSVW {

/**
 *  CSVW Namespace Vocabulary Terms
 *  <p>
 *	See <a href="https://www.w3.org/ns/csvw/">CSVW Namespace Vocabulary Terms</a>.
 *  <p>
 *  <a href="https://www.w3.org/ns/csvw/">Base URI and namepace</a>.
 */
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "https://www.w3.org/ns/csvw/";

    public static final Resource NumericFormat = m.createResource(NS+"NumericFormat");
    public static final Resource Schema = m.createResource(NS+"Schema");
    public static final Resource Dialect = m.createResource(NS+"Dialect");
    public static final Resource Row = m.createResource(NS+"Row");
    public static final Resource ForeignKey = m.createResource(NS+"ForeignKey");
    public static final Resource TableReference = m.createResource(NS+"TableReference");
    public static final Resource Cell = m.createResource(NS+"Cell");
    public static final Resource Direction = m.createResource(NS+"Direction");
    public static final Resource Table = m.createResource(NS+"Table");
    public static final Resource Datatype = m.createResource(NS+"Datatype");
    public static final Resource Column = m.createResource(NS+"Column");
    public static final Resource TableGroup = m.createResource(NS+"TableGroup");
    public static final Resource Transformation = m.createResource(NS+"Transformation");
    public static final Property aboutUrl = m.createProperty(NS+"aboutUrl");
    public static final Property groupChar = m.createProperty(NS+"groupChar");
    public static final Property _default = m.createProperty(NS+"_default");
    public static final Property propertyUrl = m.createProperty(NS+"propertyUrl");
    public static final Property scriptFormat = m.createProperty(NS+"scriptFormat");
    public static final Property base = m.createProperty(NS+"base");
    public static final Property delimiter = m.createProperty(NS+"delimiter");
    public static final Property skipRows = m.createProperty(NS+"skipRows");
    public static final Property transformations = m.createProperty(NS+"transformations");
    public static final Property virtual = m.createProperty(NS+"virtual");
    public static final Property minExclusive = m.createProperty(NS+"minExclusive");
    public static final Property commentPrefix = m.createProperty(NS+"commentPrefix");
    public static final Property length = m.createProperty(NS+"length");
    public static final Property schemaReference = m.createProperty(NS+"schemaReference");
    public static final Property rownum = m.createProperty(NS+"rownum");
    public static final Property maxLength = m.createProperty(NS+"maxLength");
    public static final Property suppressOutput = m.createProperty(NS+"suppressOutput");
    public static final Property lineTerminators = m.createProperty(NS+"lineTerminators");
    public static final Property tableSchema = m.createProperty(NS+"tableSchema");
    public static final Property separator = m.createProperty(NS+"separator");
    public static final Property headerRowCount = m.createProperty(NS+"headerRowCount");
    public static final Property table = m.createProperty(NS+"table");
    public static final Property textDirection = m.createProperty(NS+"textDirection");
    public static final Property url = m.createProperty(NS+"url");
    public static final Property row = m.createProperty(NS+"row");
    public static final Property foreignKey = m.createProperty(NS+"foreignKey");
    public static final Property skipInitialSpace = m.createProperty(NS+"skipInitialSpace");
    public static final Property lang = m.createProperty(NS+"lang");
    public static final Property trim = m.createProperty(NS+"trim");
    public static final Property pattern = m.createProperty(NS+"pattern");
    public static final Property doubleQuote = m.createProperty(NS+"doubleQuote");
    public static final Property primaryKey = m.createProperty(NS+"primaryKey");
    public static final Property maxInclusive = m.createProperty(NS+"maxInclusive");
    public static final Property decimalChar = m.createProperty(NS+"decimalChar");
    public static final Property maxExclusive = m.createProperty(NS+"maxExclusive");
    public static final Property skipColumns = m.createProperty(NS+"skipColumns");
    public static final Property format = m.createProperty(NS+"format");
    public static final Property column = m.createProperty(NS+"column");
    public static final Property source = m.createProperty(NS+"source");
    public static final Property rowTitle = m.createProperty(NS+"rowTitle");
    public static final Property ordered = m.createProperty(NS+"ordered");
    public static final Property _null = m.createProperty(NS+"_null");
    public static final Property required = m.createProperty(NS+"required");
    public static final Property targetFormat = m.createProperty(NS+"targetFormat");
    public static final Property note = m.createProperty(NS+"note");
    public static final Property reference = m.createProperty(NS+"reference");
    public static final Property tableDirection = m.createProperty(NS+"tableDirection");
    public static final Property valueUrl = m.createProperty(NS+"valueUrl");
    public static final Property name = m.createProperty(NS+"name");
    public static final Property datatype = m.createProperty(NS+"datatype");
    public static final Property dialect = m.createProperty(NS+"dialect");
    public static final Property describes = m.createProperty(NS+"describes");
    public static final Property quoteChar = m.createProperty(NS+"quoteChar");
    public static final Property minLength = m.createProperty(NS+"minLength");
    public static final Property skipBlankRows = m.createProperty(NS+"skipBlankRows");
    public static final Property minInclusive = m.createProperty(NS+"minInclusive");
    public static final Property title = m.createProperty(NS+"title");
    public static final Property encoding = m.createProperty(NS+"encoding");
    public static final Property referencedRow = m.createProperty(NS+"referencedRow");
    public static final Property header = m.createProperty(NS+"header");
    public static final Property columnReference = m.createProperty(NS+"columnReference");
    public static final Property resource = m.createProperty(NS+"resource");
}
