package com.ebremer.halcyon.wicket.ethereal;

import java.io.IOException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * Loads the demo scene-graph stack ({@code stack.jsonld} on the classpath)
 * used by the {@link Zephyr3} dev-mode viewer page.
 */
public class Stack {
    private static final String STACK_RESOURCE = "stack.jsonld";
    private static final String BASE_URI = "https://localhost:8888/ldp/utah/HnE/Stack2/";
    private static final Logger logger = LoggerFactory.getLogger(Stack.class);

    private final Model stack = ModelFactory.createDefaultModel();

    public Stack() {
        ClassPathResource cpr = new ClassPathResource(STACK_RESOURCE);
        try {
            RDFParser.create()
                    .source(cpr.getInputStream())
                    .base(BASE_URI)
                    .lang(Lang.JSONLD11)
                    .parse(stack);
        } catch (IOException ex) {
            logger.error("Failed loading {}", STACK_RESOURCE, ex);
        }
    }

    public Model getModel() {
        return stack;
    }
}
