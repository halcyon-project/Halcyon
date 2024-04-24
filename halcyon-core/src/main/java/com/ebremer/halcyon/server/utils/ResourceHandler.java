package com.ebremer.halcyon.server.utils;

import java.net.URI;

/**
 *
 * @author erich
 */
public record ResourceHandler(URI resourceBase, String urlPath) {}
