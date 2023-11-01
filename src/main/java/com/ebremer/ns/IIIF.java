package com.ebremer.ns;

import org.apache.jena.graph.* ;
import org.apache.jena.rdf.model.* ;

/**
    http://iiif.io  IIIF protocol
 */

public class IIIF {

    /**
     * The namespace of the vocabulary as a string
     */
    
    public static final String CONTEXT = """
        {
            "iiif": "http://iiif.io/api/image/2#",
            "xsd": "http://www.w3.org/2001/XMLSchema#",
            "exif": "http://www.w3.org/2003/12/exif/ns#",
            "dc": "http://purl.org/dc/elements/1.1/",
            "dcterms": "http://purl.org/dc/terms/",
            "doap": "http://usefulinc.com/ns/doap#",
            "svcs": "http://rdfs.org/sioc/services#",
            "foaf": "http://xmlns.com/foaf/0.1/",
            "sc": "http://iiif.io/api/presentation/2#",
            "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
            "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "so": "https://schema.org/",
            "csvw": "https://www.w3.org/ns/csvw/",
            "hal": "https://www.ebremer.com/halcyon/ns/",
            "baseUriRedirect": {"@id": "iiif:baseUriRedirectFeature"},
            "cors": {"@id": "iiif:corsFeature"},
            "regionByPct": {"@id": "iiif:regionByPctFeature"},
            "regionByPx": {"@id": "iiif:regionByPxFeature"},
            "regionSquare": {"@id": "iiif:regionSquareFeature"},
            "rotationArbitrary": {"@id": "iiif:arbitraryRotationFeature"},
            "rotationBy90s": {"@id": "iiif:rotationBy90sFeature"},
            "mirroring": {"@id": "iiif:mirroringFeature"},
            "sizeAboveFull": {"@id": "iiif:sizeAboveFullFeature"},
            "sizeByForcedWh": {"@id": "iiif:sizeByForcedWHFeature"},
            "sizeByH": {"@id": "iiif:sizeByHFeature"},
            "sizeByPct": {"@id": "iiif:sizeByPctFeature"},
            "sizeByW": {"@id": "iiif:sizeByWFeature"},
            "sizeByWh": {"@id": "iiif:sizeByWHFeature"},
            "sizeByWhListed": {"@id": "iiif:sizeByWHListedFeature"},
            "sizeByConfinedWh": {"@id": "iiif:sizeByConfinedWHFeature"},
            "sizeByDistortedWh": {"@id": "iiif:sizeByDistortedWHFeature"},
            "profileLinkHeader": {"@id": "iiif:profileLinkHeaderFeature"},
            "canonicalLinkHeader": {"@id": "iiif:canonicalLinkHeaderFeature"},
            "jsonldMediaType": {"@id": "iiif:jsonLdMediaTypeFeature"},
            "height": {"@id": "exif:height"},
            "width": {"@id": "exif:width"},
            "name": {"@id": "so:name"},
            "xResolution": {"@id": "exif:xResolution"},
            "yResolution": {"@id": "exif:yResolution"},            
            "resolutionUnit": {"@id": "exif:resolutionUnit"},
            "scaleFactors": {"@id": "iiif:scaleFactor", "@container": "@set"},
            "formats": {"@id": "iiif:format"},
            "qualities": {"@id": "iiif:quality"},
            "sizes": {"@id": "iiif:hasSize","@type": "@id"},
            "tiles": {"@id": "iiif:hasTile","@type": "@id"},
            "maxWidth": {"@id": "iiif:maxWidth"},
            "maxHeight": {"@id": "iiif:maxHeight"},
            "maxArea": {"@id": "iiif:maxArea"},
            "profile": {"@id": "doap:implements","@type": "@id"},
            "protocol": {"@id": "dcterms:conformsTo", "@type": "@id"},
            "supports": {"@id": "iiif:supports", "@type": "@vocab"},
            "service": {"@type": "@id", "@id": "svcs:has_service"},
            "license": {"@type": "@id", "@id": "dcterms:rights"},
            "logo": {"@type": "@id", "@id": "foaf:logo"},
            "attribution": {"@id": "sc:attributionLabel"},
            "label": {"@id": "rdfs:label"},
            "value": {"@id": "rdf:value"}
        }
    """;
    
    public static final String URI="http://iiif.io/api/image/2#";
    
    protected static final Resource resource( String local ) { return ResourceFactory.createResource(URI + local ); }

    protected static final Property property( String local ) { return ResourceFactory.createProperty(URI, local ); }

    public static final Property hasSize                    = Init.hasSize();
    public static final Property sizes                      = Init.sizes();
    public static final Property size                      = Init.size();
    public static final Property scaleFactors               = Init.scaleFactors();
    public static final Property tiles               = Init.tiles();
    public static final Property protocol               = Init.protocol();
    public static final Property formats               = Init.formats();
    public static final Property profile               = Init.profile();
    public static final Property qualities               = Init.qualities();
    public static final Property supports               = Init.supports();
    public static final Property height               = Init.height();
    public static final Property width               = Init.width();
    public static final Property preferredFormats               = Init.preferredFormats();
    
    /** Halcyon constants are used during Jena initialization.
     * <p>
     * If that initialization is triggered by touching the RDFS class,
     * then the constants are null.
     * <p>
     * So for these cases, call this helper class: Init.function()   
     */
    public static class Init {
        public static Property hasSize()                    { return property( "hasSize"); }
        public static Property sizes()                      { return property( "hasSize"); }
        public static Property size()                      { return property( "size"); }
        public static Property scaleFactors()               { return property( "scaleFactor"); }
        public static Property tiles()                      { return property( "hasTile"); }
        public static Property formats()                      { return property( "format"); }
        public static Property profile()                      { return ResourceFactory.createProperty("http://usefulinc.com/ns/doap#", "implements" ); }
        public static Property protocol()                      { return ResourceFactory.createProperty("http://purl.org/dc/terms/", "conformsTo" ); }
        public static Property qualities()                      { return property( "quality"); }
        public static Property supports()                      { return property( "supports"); }
        public static Property height()                      { return ResourceFactory.createProperty("http://www.w3.org/2003/12/exif/ns#", "height"); }
        public static Property width()                      { return ResourceFactory.createProperty("http://www.w3.org/2003/12/exif/ns#", "width"); }
        public static Property preferredFormats()                      { return property( "preferredFormats"); }
    }
    
    /**
        The Schema.org vocabulary, expressed for the SPI layer in terms of .graph Nodes.
    */
    @SuppressWarnings("hiding") public static class Nodes {
        public static final Node hasSize                    = Init.hasSize().asNode();
        public static final Node sizes                      = Init.sizes().asNode();
        public static final Node size                      = Init.size().asNode();
        public static final Node scaleFactors               = Init.scaleFactors().asNode();
        public static final Node tiles               = Init.tiles().asNode();
        public static final Node protocol               = Init.protocol().asNode();
        public static final Node formats               = Init.formats().asNode();
        public static final Node profile               = Init.profile().asNode();
        public static final Node qualities               = Init.qualities().asNode();
        public static final Node supports               = Init.supports().asNode();
        public static final Node height               = Init.height().asNode();
        public static final Node width               = Init.width().asNode();
        public static final Node preferredFormats               = Init.preferredFormats().asNode();
    }

    /**
        returns the URI for this schema
        @return the URI for this schema
    */
    public static String getURI() {
        return URI;
    }
}

