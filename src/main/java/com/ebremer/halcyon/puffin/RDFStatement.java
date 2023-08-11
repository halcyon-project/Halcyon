package com.ebremer.halcyon.puffin;

import com.ebremer.halcyon.gui.HalcyonSession;
import java.util.UUID;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class RDFStatement implements IModel<Object> {
    private final String uuid;

    public RDFStatement(Statement m) {
        super();
        this.uuid = UUID.randomUUID().toString();
        HalcyonSession.get().getBlock().getEphemeralStatementStorage().put(uuid, m);
    }
    
    public Statement getStatement() {
        return HalcyonSession.get().getBlock().getEphemeralStatementStorage().get(uuid);
    }

    @Override
    public Object getObject() {
        Object x = HalcyonSession.get().getBlock().getEphemeralStatementStorage().get(uuid).getObject();
        System.out.println("getObject --> "+x+" <------ "+x.getClass().toGenericString());
        switch (x) {
            case Literal n -> { return n.getValue(); }
            case Resource r -> { return r; }
            default -> { throw new Error("arrrggg "+x.getClass().toGenericString()); }
        }
    }
    
    public Class getObjectClass() {
        RDFNode x = HalcyonSession.get().getBlock().getEphemeralStatementStorage().get(uuid).getObject();
        System.out.println("getObjectClass --> "+x+" <------ "+x.getClass().toGenericString());
        if (x instanceof Literal n) {
            return n.getValue().getClass();
        } else if (x instanceof Resource) {
            return Resource.class;
        } else {
            throw new Error("cant handle this");
        }
    }

    @Override
    public void setObject(Object object) {
        System.out.println("setObject --> "+object +" <------ "+object.getClass().toGenericString());
        Statement s = HalcyonSession.get().getBlock().getEphemeralStatementStorage().get(uuid);
        switch (object) {
            case Float o -> s.changeLiteralObject(o);
            case Integer o -> s.changeLiteralObject(o);
            case String o -> s.changeObject(o);
            case Resource o -> s.changeObject(o);
            case Boolean o -> s.changeLiteralObject(o);
            default -> throw new Error("cant handle this either "+object.getClass().toGenericString());
        }
    }
}
