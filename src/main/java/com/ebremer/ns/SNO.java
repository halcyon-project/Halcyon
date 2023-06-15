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
import org.apache.jena.rdf.model.Resource;

public class SNO {

    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://snomed.info/id/";

    public static final Resource Lymphocytes = m.createResource(NS+"56972008");
    public static final Resource TumorCell = m.createResource(NS+"252987004");
    public static final Resource Cell = m.createResource(NS+"362837007");
    public static final Resource Unknown = m.createResource(NS+"261665006");
    public static final Resource Misc = m.createResource(NS+"49634009");
    public static final Resource Tumor = m.createResource(NS+"108369006");
    public static final Resource Stroma = m.createResource(NS+"1153387007");
    public static final Resource Necrosis = m.createResource(NS+"6574001");
    public static final Resource Epithelium = m.createResource(NS+"31610004");
    public static final Resource Dysplasia = m.createResource(NS+"25723000");
    public static final Resource NuclearMaterial = m.createResource(NS+"48512009");
    public static final Resource Connectivetissue = m.createResource(NS+"21793004");
}
