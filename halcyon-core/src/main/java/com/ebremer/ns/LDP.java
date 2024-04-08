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

public class LDP {

/**
 *  The W3C Linked Data Platform (LDP) Vocabulary
 *  <p>
 *	See <a href="https://www.w3.org/ns/ldp">The W3C Linked Data Platform (LDP) Vocabulary</a>.
 *  <p>
 *  <a href="http://www.w3.org/ns/ldp#>Base URI and namepace</a>.
 */
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://www.w3.org/ns/ldp#";

    public static final Resource BasicContainer = m.createResource(NS+"BasicContainer");
    public static final Resource Container = m.createResource(NS+"Container");
    public static final Resource DirectContainer = m.createResource(NS+"DirectContainer");
    public static final Resource IndirectContainer = m.createResource(NS+"IndirectContainer");
    public static final Resource NonRDFSource = m.createResource(NS+"NonRDFSource");
    public static final Resource Page = m.createResource(NS+"Page");
    public static final Resource PageSortCriterion = m.createResource(NS+"PageSortCriterion");
    public static final Resource RDFSource = m.createResource(NS+"RDFSource");
    public static final Resource Resource = m.createResource(NS+"Resource");
    public static final Property constrainedBy = m.createProperty(NS+"constrainedBy");
    public static final Property contains = m.createProperty(NS+"contains");
    public static final Property hasMemberRelation = m.createProperty(NS+"hasMemberRelation");
    public static final Property inbox = m.createProperty(NS+"inbox");
    public static final Property insertedContentRelation = m.createProperty(NS+"insertedContentRelation");
    public static final Property isMemberOfRelation = m.createProperty(NS+"isMemberOfRelation");
    public static final Property member = m.createProperty(NS+"member");
    public static final Property membershipResource = m.createProperty(NS+"membershipResource");
    public static final Property pageSequence = m.createProperty(NS+"pageSequence");
    public static final Property pageSortCollation = m.createProperty(NS+"pageSortCollation");
    public static final Property pageSortCriteria = m.createProperty(NS+"pageSortCriteria");
    public static final Property pageSortOrder = m.createProperty(NS+"pageSortOrder");
    public static final Property pageSortPredicate = m.createProperty(NS+"pageSortPredicate");
}
