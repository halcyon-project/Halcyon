package com.ebremer.halcyon.client.utils;

import com.beust.jcommander.IStringConverter;
import java.net.URI;

public class URIConverter implements IStringConverter<URI> {

    @Override
    public URI convert(String value) {
        return URI.create(value);
    }
}
