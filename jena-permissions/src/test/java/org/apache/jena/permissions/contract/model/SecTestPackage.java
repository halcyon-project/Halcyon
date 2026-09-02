/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jena.permissions.contract.model;

import org.apache.jena.atlas.web.TypedInputStream;
import org.apache.jena.irix.IRIs;
import org.apache.jena.permissions.MockSecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.AbstractTestPackage;
import org.apache.jena.rdf.model.helpers.ModelCreator;
import org.apache.jena.riot.system.streammgr.Locator;
import org.apache.jena.riot.system.streammgr.LocatorZip;
import org.apache.jena.riot.system.streammgr.StreamManager;

import junit.framework.TestSuite;

/**
 * Test package to test Model implementation.
 */
public class SecTestPackage extends AbstractTestPackage {
    static public TestSuite suite() throws SecurityException, IllegalArgumentException {
        return new SecTestPackage();
    }

    public SecTestPackage() throws SecurityException, IllegalArgumentException {
        super("SecuredModelTest", new PlainModelFactory());
        // register a jar reader here
        StreamManager sm = StreamManager.get();
        sm.addLocator(new LocatorJarURL());
    }

    /**
     * Jena 6.2.0 replaced {@code TestingModelFactory} with {@link ModelCreator}, which extends
     * {@code Creator<Model>} and so declares only {@code create()}. The two methods that went with
     * it -- {@code getPrefixMapping()} and {@code createModel(Graph)} -- have no callers in the
     * upstream suite any more, and neither was doing security work here: the first delegated
     * straight to the model this creates, the second wrapped a caller-supplied graph WITHOUT
     * securing it. Only the secured construction below is load-bearing, and it is unchanged.
     */
    /* package private */static class PlainModelFactory implements ModelCreator {
        private final SecurityEvaluator eval;

        public PlainModelFactory() {
            eval = new MockSecurityEvaluator(true, true, true, true, true, true, true);
        }

        @Override
        public Model create() {
            final Model model = ModelFactory.createDefaultModel();
            return org.apache.jena.permissions.Factory.getInstance(eval, "testModel", model);
        }
    }

    public static class LocatorJarURL implements Locator {

        @Override
        public TypedInputStream open(String uri) {
            String uriSchemeName = IRIs.scheme(uri);
            if (!"jar".equalsIgnoreCase(uriSchemeName)) {
                return null;
            }

            String[] parts = uri.substring(4).split("!");
            if (parts.length != 2) {
                return null;
            }
            if (parts[0].toLowerCase().startsWith("file:")) {
                parts[0] = parts[0].substring(5);
            }
            if (parts[1].startsWith("/")) {
                parts[1] = parts[1].substring(1);
            }
            LocatorZip zl = new LocatorZip(parts[0]);
            return zl.open(parts[1]);
        }

        @Override
        public String getName() {
            return "JarURLLocator";
        }

    }
}
