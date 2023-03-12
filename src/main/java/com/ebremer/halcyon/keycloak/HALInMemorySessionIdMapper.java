package com.ebremer.halcyon.keycloak;

import org.keycloak.adapters.spi.InMemorySessionIdMapper;

/**
 *
 * @author erich
 */
public class HALInMemorySessionIdMapper extends InMemorySessionIdMapper {

    @Override
    public boolean hasSession(String id) {
        System.out.println("HASSESSION --> "+id+"  "+super.hasSession(id));
        return super.hasSession(id);
    }
}
