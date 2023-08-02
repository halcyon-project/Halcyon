package com.ebremer.halcyon.puffin;

import java.util.Locale;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.wicket.util.convert.ConversionException;
import org.apache.wicket.util.convert.IConverter;

/**
 *
 * @author erich
 */
public class ResourceConverter implements IConverter<Resource> {

    @Override
    public Resource convertToObject(String value, Locale locale) throws ConversionException {
        return ResourceFactory.createResource(value);
    }

    @Override
    public String convertToString(Resource value, Locale locale) {
        return value.toString();
    }
}
