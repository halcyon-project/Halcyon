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
package org.apache.jena.permissions.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.MockSecurityEvaluator;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.junit.Test;

/**
 * H3: the node iterator returned by {@code SecuredContainer.iterator()} must not
 * let a principal with only Read delete a member via {@code iterator().remove()}.
 */
public class SecuredContainerIteratorRemoveTest {

    private static Bag freshBag() {
        final Model m = ModelFactory.createDefaultModel();
        final Bag b = m.createBag();
        b.add(m.createLiteral("A"));
        b.add(m.createLiteral("B"));
        b.add(m.createLiteral("C"));
        return b;
    }

    // loggedIn, create, read, update, delete, forceTripleChecks, hardReadError
    private static Bag secure(final Bag bag, final boolean update, final boolean delete) {
        final MockSecurityEvaluator eval = new MockSecurityEvaluator(true, true, true, update, delete, false, true);
        final Model sm = Factory.getInstance(eval, "http://example.com/model", bag.getModel());
        return sm.getBag(bag);
    }

    @Test
    public void iteratorRemove_deniedWhenDeleteDenied() {
        final Bag bag = freshBag();
        final Bag secured = secure(bag, true, false); // read+update yes, delete NO
        final NodeIterator it = secured.iterator();
        try {
            it.next();
            it.remove();
            fail("iterator().remove() must not delete a member without Delete permission");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("no member should have been deleted", 3, bag.size());
    }

    @Test
    public void iteratorRemove_deniedWhenUpdateDenied() {
        final Bag bag = freshBag();
        final Bag secured = secure(bag, false, true); // read yes, update NO
        final NodeIterator it = secured.iterator();
        try {
            it.next();
            it.remove();
            fail("iterator().remove() must not delete a member without Update permission");
        } catch (final UpdateDeniedException expected) {
            // correct
        }
        assertEquals("no member should have been deleted", 3, bag.size());
    }

    @Test
    public void iteratorRemove_permitted_succeeds() {
        final Bag bag = freshBag();
        final Bag secured = secure(bag, true, true);
        final NodeIterator it = secured.iterator();
        it.next();
        it.remove();
        assertTrue("a member should have been deleted", bag.size() < 3);
    }
}
