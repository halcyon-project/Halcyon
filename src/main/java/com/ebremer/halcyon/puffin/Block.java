package com.ebremer.halcyon.puffin;

/**
 *
 * @author erich
 */
public class Block implements AutoCloseable {
    
    private final EphemeralStatementStorage ess;
    private final EphemeralQuerySolutionStorage eqss;
    private final EphemeralResourceStorage ers;
    
    public Block() {
        ess = new EphemeralStatementStorage();
        eqss = new EphemeralQuerySolutionStorage();
        ers = new EphemeralResourceStorage();
    }
    
    public EphemeralStatementStorage getEphemeralStatementStorage() {
        return ess;
    }

    public EphemeralQuerySolutionStorage getEphemeralQuerySolutionStorage() {
        return eqss;
    }

    public EphemeralResourceStorage getEphemeralResourceStorage() {
        return ers;
    }
    
    @Override
    public void close() {
        ess.close();
        eqss.close();
    }
}
