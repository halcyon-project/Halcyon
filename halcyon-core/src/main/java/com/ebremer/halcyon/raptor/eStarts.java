package com.ebremer.halcyon.raptor;

import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase1;

public class eStarts extends FunctionBase1 {

    @Override
    public NodeValue exec(NodeValue v) {
        if (!v.isString()) {
            throw new IllegalArgumentException("IsEvenFunction expects an String argument");
        }
        return NodeValue.makeBoolean(v.getString().startsWith("urn:uuid:311"));
    }
}
