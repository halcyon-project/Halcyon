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

public class SOLID {

/**
 *  The Solid Terms vocabulary defines terms referenced in Solid specifications
 *  <p>
 *	See <a href="http://www.w3.org/ns/solid/terms#">The Solid Terms vocabulary</a>.
 *  <p>
 *  <a href="http://www.w3.org/ns/solid/terms#>Base URI and namepace</a>.
 */
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://www.w3.org/ns/solid/terms#";

    public static final Resource Account = m.createResource(NS+"Account");
    public static final Resource Inbox = m.createResource(NS+"Inbox");
    public static final Resource InsertDeletePatch = m.createResource(NS+"InsertDeletePatch");
    public static final Resource ListedDocument = m.createResource(NS+"ListedDocument");
    public static final Resource Notification = m.createResource(NS+"Notification");
    public static final Resource Patch = m.createResource(NS+"Patch");
    public static final Resource Timeline = m.createResource(NS+"Timeline");
    public static final Resource TypeIndex = m.createResource(NS+"TypeIndex");
    public static final Resource TypeRegistration = m.createResource(NS+"TypeRegistration");
    public static final Resource UnlistedDocument = m.createResource(NS+"UnlistedDocument");
    public static final Property account = m.createProperty(NS+"account");
    public static final Property deletes = m.createProperty(NS+"deletes");
    public static final Property forClass = m.createProperty(NS+"forClass");
    public static final Property inbox = m.createProperty(NS+"inbox");
    public static final Property inserts = m.createProperty(NS+"inserts");
    public static final Property instance = m.createProperty(NS+"instance");
    public static final Property instanceContainer = m.createProperty(NS+"instanceContainer");
    public static final Property loginEndpoint = m.createProperty(NS+"loginEndpoint");
    public static final Property logoutEndpoint = m.createProperty(NS+"logoutEndpoint");
    public static final Property notification = m.createProperty(NS+"notification");
    public static final Property oidcIssuer = m.createProperty(NS+"oidcIssuer");
    public static final Property owner = m.createProperty(NS+"owner");
    public static final Property patches = m.createProperty(NS+"patches");
    public static final Property privateLabelIndex = m.createProperty(NS+"privateLabelIndex");
    public static final Property privateTypeIndex = m.createProperty(NS+"privateTypeIndex");
    public static final Property publicTypeIndex = m.createProperty(NS+"publicTypeIndex");
    public static final Property read = m.createProperty(NS+"read");
    public static final Property storageQuota = m.createProperty(NS+"storageQuota");
    public static final Property storageUsage = m.createProperty(NS+"storageUsage");
    public static final Property timeline = m.createProperty(NS+"timeline");
    public static final Property typeIndex = m.createProperty(NS+"typeIndex");
    public static final Property where = m.createProperty(NS+"where");
}
