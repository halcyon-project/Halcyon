package com.ebremer.halcyon.puffin;

import java.util.UUID;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 * @param <T>
 */
public class RDFStatement<T> implements IModel<Object> {
    private final String uuid;

    public RDFStatement(Statement m) {
        super();
        this.uuid = UUID.randomUUID().toString();
        EphemeralStatementStorage.getInstance().put(uuid, m);
    }

    @Override
    public Object getObject() {
        Object x = EphemeralStatementStorage.getInstance().get(uuid).getObject();
        System.out.println("getObject --> "+x+" <------ "+x.getClass().toGenericString());
        switch (x) {
            case Literal n -> {
                return n.getValue();
            }
            case Resource r -> {
                return r;
            }
            default -> {
            }
        }
        throw new Error("arrrggg "+x.getClass().toGenericString());
    }
    
    public Class getObjectClass() {
        RDFNode x = EphemeralStatementStorage.getInstance().get(uuid).getObject();
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
        Statement s = EphemeralStatementStorage.getInstance().get(uuid);
        switch (object) {
            case Float o -> s.changeLiteralObject(o);
            case Integer o -> s.changeLiteralObject(o);
            case String o -> s.changeObject(o);
            default -> throw new Error("cant handle this either");
        }
        RDFDataMgr.write(System.out, s.getModel(), Lang.TURTLE);
    }
}
