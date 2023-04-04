package com.ebremer.ethereal;

import java.util.LinkedList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

/**
 *
 * @author erich
 */
public class MakeList {
    
    public static List Of(ResultSet rs, String target) {
        List<Node> list = new LinkedList<>();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            list.add(qs.get(target).asNode());
        }
        return list;
    }
}
