package com.ebremer.halcyon.puffin;

import java.util.HashMap;
import java.util.LinkedHashSet;
import org.apache.jena.graph.Node;

/**
 *
 * @author erich
 */
public record HShape(
    Node shape,
    LinkedHashSet<Node> properties,
    HashMap<Node, Integer> orders,
    HashMap<Node, Node> datatypes,
    HashMap<Node, Node> editors,
    HashMap<Node, Node> viewers,
    HashMap<Node, Object> defaultValue,
    HashMap<Node, Node> nodeKind,
    HashMap<Node, Integer> minCount,
    HashMap<Node, Integer> maxCount
) {}
