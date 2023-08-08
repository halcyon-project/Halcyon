package com.ebremer.halcyon.puffin;

import java.util.ArrayList;
import org.apache.jena.graph.Node;

/**
 *
 * @author erich
 */
public class PropertyData {
    private final Node subject;
    private final Node property;
    private final Node shape;
    private final ArrayList<String> pmessages;
    private final ArrayList<String> messages;
    
    public PropertyData(Node subject, Node property, Node shape) {
        this.subject = subject;
        this.property = property;
        this.shape = shape;
        this.pmessages = new ArrayList<>();
        this.messages = new ArrayList<>();
    }

    public Node getSubject() {
        return subject;
    }

    public Node getProperty() {
        return property;
    }
    
    public ArrayList<String> getmessages() {
        return messages;
    }

    public ArrayList<String> getpmessages() {
        return pmessages;
    }
}
