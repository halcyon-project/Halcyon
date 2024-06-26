package com.ebremer.halcyon.lib.spatial;

import com.ebremer.halcyon.server.utils.PathMapper;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.expr.ExprList;

public class FileToHttpFunction extends FunctionBase {

    @Override
    public NodeValue exec(List<NodeValue> args) {
        NodeValue v = args.get(0);
        String uri = v.asNode().getURI();
        Optional<URI> huri = PathMapper.getPathMapper().file2http(uri);
        if (huri.isPresent()) {
            return NodeValue.makeNode(NodeFactory.createURI(huri.get().toString()));
        }      
        return v;
    }

    @Override
    public void checkBuild(String string, ExprList args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("FileToHttpFunction expects one argument");
        }
    }
}

