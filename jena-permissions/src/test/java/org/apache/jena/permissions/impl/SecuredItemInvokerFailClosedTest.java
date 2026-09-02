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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.jena.permissions.MockSecurityEvaluator;
import org.apache.jena.permissions.SecuredItem;
import org.apache.jena.permissions.SecurityEvaluator;
import org.junit.Test;

/**
 * L12: an interface method the Secured* implementation does not declare must
 * NOT be forwarded to the unsecured base item. The proxy exposes every
 * interface of the base object (see {@code ItemHolder.setSecuredItem}), so the
 * old transparent forwarding meant one new interface on a base class — or one
 * new method from a Jena upgrade — silently opened an unchecked path around
 * every permission check. The invoker now fails closed.
 */
public class SecuredItemInvokerFailClosedTest {

    /** An interface only the base item implements — never the secured wrapper. */
    public interface Unsecured {
        String reveal();
    }

    public static class UnsecuredBase implements Unsecured {
        @Override
        public String reveal() {
            return "base data that must not be reachable through the proxy";
        }
    }

    /** Minimal concrete SecuredItemImpl that does NOT implement Unsecured. */
    private static class DummySecuredItem extends SecuredItemImpl {
        DummySecuredItem(final SecurityEvaluator evaluator, final ItemHolder<?, ?> holder) {
            super(evaluator, "http://example.com/dummy", holder);
        }
    }

    @Test
    public void unimplementedInterfaceMethodFailsClosed() {
        final ItemHolder<Unsecured, SecuredItem> holder = new ItemHolder<>(new UnsecuredBase());
        final DummySecuredItem item = new DummySecuredItem(MockSecurityEvaluator.getInstance(), holder);
        final SecuredItem proxy = holder.setSecuredItem(new SecuredItemInvoker(UnsecuredBase.class, item));

        assertTrue("the proxy exposes the base item's interfaces", proxy instanceof Unsecured);
        try {
            ((Unsecured) proxy).reveal();
            fail("an interface method the secured wrapper does not implement must not reach the base item");
        } catch (final UnsupportedOperationException expected) {
            assertTrue("the error should name the unproxied method: " + expected.getMessage(),
                    expected.getMessage().contains("reveal"));
        }

        // methods the wrapper does implement still dispatch to it
        assertEquals("http://example.com/dummy", proxy.getModelIRI());
        // and the Object-method special cases are untouched
        assertNotNull(proxy.toString());
        proxy.hashCode();
        assertTrue(proxy.equals(proxy));
    }
}
