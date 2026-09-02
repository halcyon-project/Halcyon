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
package org.apache.jena.permissions.impl;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.MockSecurityEvaluator;
import org.apache.jena.permissions.model.SecuredModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.GraphView;
import org.junit.Test;

/**
 * L12: {@code SecuredItemInvoker} fails closed for any interface method the
 * Secured* implementation does not declare (it used to forward such calls to
 * the unsecured base item). That is only safe if, for the types this module
 * actually proxies, every method reachable through the proxy resolves on the
 * secured implementation — otherwise legitimate calls die at runtime with
 * {@code UnsupportedOperationException}.
 * <p>
 * This test pins that coverage at build time: for each secured proxy reachable
 * through the public factories (model, graph — memory- and GraphView-backed —
 * prefix mapping, statement, resource, property, literal, bag, alt, seq,
 * RDFList, statement term) it enumerates every non-static method of every
 * interface the proxy exposes and asserts the method resolves on the
 * implementation class, exactly as the invoker resolves it. A Jena upgrade
 * that adds an interface method the wrappers miss fails here instead of
 * surprising a caller in production.
 */
public class SecuredItemInvokerCoverageTest {

    private static java.util.List<String> unresolved(final String label, final Object proxy) {
        final SecuredItemInvoker invoker = (SecuredItemInvoker) Proxy.getInvocationHandler(proxy);
        final Object impl = invoker.securedItem;
        final TreeSet<String> missing = new TreeSet<>();
        for (final Class<?> iface : proxy.getClass().getInterfaces()) {
            for (final Method m : iface.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.isSynthetic()) {
                    continue;
                }
                try {
                    // the same resolution SecuredItemInvoker.invoke performs
                    impl.getClass().getMethod(m.getName(), m.getParameterTypes());
                } catch (final NoSuchMethodException e) {
                    missing.add(String.format("%s: %s#%s(%s)", label, iface.getSimpleName(), m.getName(),
                            Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName)
                                    .collect(Collectors.joining(", "))));
                }
            }
        }
        return new java.util.ArrayList<>(missing);
    }

    @Test
    public void everyProxiedInterfaceMethodResolvesOnTheSecuredImplementation() {
        // everything permitted; no forced triple checks
        final MockSecurityEvaluator eval = new MockSecurityEvaluator(true, true, true, true, true, false, true);

        final Model base = ModelFactory.createDefaultModel();
        final Resource s = base.createResource("http://example.com/s");
        final Property p = base.createProperty("http://example.com/p");
        base.add(s, p, s);
        base.add(s, p, "literal");
        final Resource bag = base.createBag("http://example.com/bag");
        final Resource alt = base.createAlt("http://example.com/alt");
        final Resource seq = base.createSeq("http://example.com/seq");

        final SecuredModel model = Factory.getInstance(eval, "http://example.com/securedModel", base);
        final Statement statement = model.listStatements(s, p, (RDFNode) null).next();

        final Map<String, Object> proxies = new LinkedHashMap<>();
        proxies.put("model", model);
        proxies.put("graph(mem)", model.getGraph());
        proxies.put("graph(GraphView)", Factory.getInstance(eval, "http://example.com/securedGraphView",
                GraphView.createDefaultGraph(DatasetGraphFactory.createTxnMem())));
        proxies.put("prefixMapping", model.getGraph().getPrefixMapping());
        proxies.put("statement", statement);
        proxies.put("resource", statement.getSubject());
        proxies.put("property", statement.getPredicate());
        proxies.put("literal", model.listStatements(s, p, "literal").next().getLiteral());
        proxies.put("bag", model.getBag(bag));
        proxies.put("alt", model.getAlt(alt));
        proxies.put("seq", model.getSeq(seq.getURI()));
        proxies.put("rdfList", model.createList(s));
        proxies.put("statementTerm", model.createStatementTerm(base.listStatements(s, p, (RDFNode) null).next()));

        final java.util.List<String> missing = new java.util.ArrayList<>();
        proxies.forEach((label, proxy) -> missing.addAll(unresolved(label, proxy)));

        assertTrue("Methods reachable through a secured proxy but not implemented by its secured wrapper"
                + " (the invoker now fails closed, so each of these would throw at runtime):\n  "
                + String.join("\n  ", missing), missing.isEmpty());
    }
}
