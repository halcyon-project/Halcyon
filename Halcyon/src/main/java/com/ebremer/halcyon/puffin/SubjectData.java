package com.ebremer.halcyon.puffin;

import java.util.ArrayList;
import org.apache.jena.graph.Node;

/**
 *
 * @author erich
 */
public class SubjectData {
    private final Node subject;
    private final Node shape;
    private final ArrayList<String> properties;
    private final ArrayList<String> messages;

    public SubjectData(Node subject, Node shape) {
        this.subject = subject;
        this.shape = shape;
        this.properties = new ArrayList<>();
        this.messages = new ArrayList<>();
    }
}
