package com.ebremer.halcyon.puffin;

import com.ebremer.halcyon.gui.HalcyonSession;
import java.util.UUID;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class DetachableStatement extends LoadableDetachableModel<Statement> {
    private final String uuid;

    public DetachableStatement(Statement statement) {
        super();
        this.uuid = UUID.randomUUID().toString();
        HalcyonSession.get().getBlock().getEphemeralStatementStorage().put(uuid, statement);
    }
    
    public DetachableStatement(String uuid, Resource m) {
        super();
        this.uuid = uuid;
        HalcyonSession.get().getBlock().getEphemeralResourceStorage().put(uuid, m);
    }

    @Override
    public Statement load() {
        return HalcyonSession.get().getBlock().getEphemeralStatementStorage().get(uuid);
    }
    
    @Override
    public void detach() {
        super.detach();
    }
}
