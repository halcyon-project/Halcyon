package com.ebremer.halcyon.lib.GeoSPARQL;

import com.ebremer.ns.GEO;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

/**
 * A class containing a builder to create a GeoSPARQL FeatureCollection as an Apache Jena Model.
 * The structure follows a specific pattern including provenance and feature details.
 *
 * @author Erich Bremer
 */
public class FeatureCollection {

    // Vocabulary constants for custom namespaces
    private static final String GEO_NS = "http://www.opengis.net/ont/geosparql#";
    private static final String HAL_NS = "https://halcyon.is/ns/";
    private static final String PROV_NS = "http://www.w3.org/ns/prov#";
    private static final String SO_NS = "https://schema.org/";
    private static final String EXIF_NS = "http://www.w3.org/2003/12/exif/ns#";
    private static final String SNO_NS = "http://snomed.info/id/";

    // Vocabulary Resources (Classes)
    private static final Resource GEO_FeatureCollection = ResourceFactory.createResource(GEO_NS + "FeatureCollection");
    private static final Resource GEO_Feature = ResourceFactory.createResource(GEO_NS + "Feature");
    private static final Resource PROV_Activity = ResourceFactory.createResource(PROV_NS + "Activity");
    private static final Resource SO_ImageObject = ResourceFactory.createResource(SO_NS + "ImageObject");

    // Vocabulary Properties
    private static final Property GEO_hasGeometry = ResourceFactory.createProperty(GEO_NS + "hasGeometry");
    private static final Property GEO_asWKT = ResourceFactory.createProperty(GEO_NS + "asWKT");
    private static final Property PROV_wasGeneratedBy = ResourceFactory.createProperty(PROV_NS + "wasGeneratedBy");
    private static final Property PROV_used = ResourceFactory.createProperty(PROV_NS + "used");
    private static final Property PROV_wasAssociatedWith = ResourceFactory.createProperty(PROV_NS + "wasAssociatedWith");
    private static final Property HAL_classification = ResourceFactory.createProperty(HAL_NS + "classification");
    private static final Property HAL_measurement = ResourceFactory.createProperty(HAL_NS + "measurement");
    private static final Property HAL_hasProbability = ResourceFactory.createProperty(HAL_NS + "hasProbability");
    private static final Property EXIF_height = ResourceFactory.createProperty(EXIF_NS + "height");
    private static final Property EXIF_width = ResourceFactory.createProperty(EXIF_NS + "width");

    /**
     * A builder for creating a GeoSPARQL FeatureCollection as an Apache Jena Model.
     */
    public static class Builder {

        private String title;
        private String description;
        private String creator; // e.g., ORCID URL
        private ZonedDateTime date;
        private final List<String> publishers = new ArrayList<>(); // e.g., ROR URLs
        private String references; // e.g., DOI URL
        private String wasGeneratedByAgent; // e.g., GitHub release URL
        private String sourceImageURI; // e.g., urn:md5:a923c8367e61792f531e65d966d4cb78
        private Integer imageWidth;
        private Integer imageHeight;
        private String root = null;
        
        private List<String> wktList;
        private String defaultClassificationSnomedCode;
        private float defaultProbability = 1.0f;

        public Builder() {}

        // --- Setters for FeatureCollection Metadata ---

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder creator(String creatorUri) {
            this.creator = creatorUri;
            return this;
        }

        public Builder date(ZonedDateTime date) {
            this.date = date;
            return this;
        }
        
        public Builder addPublisher(String publisherUri) {
            this.publishers.add(publisherUri);
            return this;
        }

        public Builder setRoot(String root) {
            this.root = root;
            this.root = "";
            return this;
        }        
        
        public Builder references(String referencesUri) {
            this.references = referencesUri;
            return this;
        }

        // --- Setters for Provenance ---

        public Builder wasGeneratedByAgent(String agentUri) {
            this.wasGeneratedByAgent = agentUri;
            return this;
        }

        public Builder sourceImage(String uri, int width, int height) {
            this.sourceImageURI = uri.trim();
            this.imageWidth = width;
            this.imageHeight = height;
            return this;
        }
        
        // --- Setters for Feature Data ---

        /**
         * Sets the list of WKT strings that represent the feature geometries.
         * @param wktList A list of Polygons in WKT format.
         * @return The builder instance for chaining.
         */
        public Builder setWkt(List<String> wktList) {
            this.wktList = wktList;
            return this;
        }

        /**
         * Sets the default SNOMED CT classification code for all features.
         * @param snomedCode The SNOMED CT code (e.g., "48512009").
         * @return The builder instance for chaining.
         */
        public Builder setDefaultClassification(String snomedCode) {
            this.defaultClassificationSnomedCode = snomedCode;
            return this;
        }
        
        /**
         * Sets the default probability for the classification measurement. Defaults to 1.0.
         * @param probability The probability value.
         * @return The builder instance for chaining.
         */
        public Builder setDefaultProbability(float probability) {
            this.defaultProbability = probability;
            return this;
        }

        /**
         * Builds the Apache Jena Model representing the GeoSPARQL FeatureCollection.
         * @return An Apache Jena Model populated with the builder's data.
         */
        public Model build() {
            Model model = ModelFactory.createDefaultModel();
            setNsPrefixes(model);

            // Create the main FeatureCollection resource (as a blank node)
            Resource fc;
            if (root == null) {
                fc = model.createResource();
            } else {
                fc = model.createResource(root);
            }
            fc.addProperty(RDF.type, GEO_FeatureCollection);

            // Add metadata
            if (title != null) fc.addProperty(DCTerms.title, title);
            if (description != null) fc.addProperty(DCTerms.description, description);
            if (creator != null) fc.addProperty(DCTerms.creator, model.createResource(creator));
            
            ZonedDateTime creationDate = (date != null) ? date : ZonedDateTime.now();
            Literal dateLiteral = model.createTypedLiteral(creationDate.format(DateTimeFormatter.ISO_INSTANT), XSD.dateTime.getURI());
            fc.addProperty(DCTerms.date, dateLiteral);
            
            publishers.forEach(pub -> fc.addProperty(DCTerms.publisher, model.createResource(pub)));
            
            if (references != null) fc.addProperty(DCTerms.references, model.createResource(references));

            // Create the source image object if specified
            Resource imageResource = null;
            if (sourceImageURI != null) {
                imageResource = model.createResource(sourceImageURI);
                imageResource.addProperty(RDF.type, SO_ImageObject);
                if (imageWidth != null) imageResource.addLiteral(EXIF_width, imageWidth);
                if (imageHeight != null) imageResource.addLiteral(EXIF_height, imageHeight);
            }
            
            // Create and link provenance information
            if (wasGeneratedByAgent != null || imageResource != null) {
                Resource activity = model.createResource()
                    .addProperty(RDF.type, PROV_Activity);
                
                if (imageResource != null) {
                    activity.addProperty(PROV_used, imageResource);
                }
                if (wasGeneratedByAgent != null) {
                    activity.addProperty(PROV_wasAssociatedWith, model.createResource(wasGeneratedByAgent));
                }
                fc.addProperty(PROV_wasGeneratedBy, activity);
            }
            
            // Create and add all features as members
            if (wktList != null && !wktList.isEmpty() && defaultClassificationSnomedCode != null) {
                //Resource classificationResource = model.createResource(SNO_NS + defaultClassificationSnomedCode);
                String classificationResource = defaultClassificationSnomedCode;
                Literal probLiteral = model.createTypedLiteral(defaultProbability, XSD.xfloat.getURI());

                for (String wkt : wktList) {
                    Resource feature = createFeature(model, wkt, classificationResource, probLiteral);
                    fc.addProperty(RDFS.member, feature);
                }
            }
            return model;
        }

        private Resource createFeature(Model model, String wkt, String classification, Literal probability) {
            // Create a blank node for the Geometry
            Resource geometry = model.createResource()
                .addLiteral(GEO_asWKT, model.createTypedLiteral(wkt, GEO.wktLiteral.getURI()));

            // Create a blank node for the Measurement
            //Resource measurement = model.createResource()
              //  .addProperty(HAL_classification, classification)
                //.addProperty(HAL_hasProbability, probability);

            // Create the Feature blank node and link its components
            return model.createResource()
                .addProperty(RDF.type, GEO_Feature)
                .addProperty(GEO_hasGeometry, geometry)
                .addProperty(HAL_classification, classification);
                //.addProperty(HAL_measurement, measurement);
        }

        private void setNsPrefixes(Model model) {
            model.setNsPrefix("dc", DCTerms.NS);
            model.setNsPrefix("exif", EXIF_NS);
            model.setNsPrefix("geo", GEO_NS);
            model.setNsPrefix("hal", HAL_NS);
            model.setNsPrefix("prov", PROV_NS);
            model.setNsPrefix("rdfs", RDFS.uri);
            model.setNsPrefix("sno", SNO_NS);
            model.setNsPrefix("sdo", SO_NS);
            model.setNsPrefix("xsd", XSD.NS);
        }
    }
}