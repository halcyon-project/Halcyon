package com.ebremer.halcyon.puffin;

/**
 *
 * @author erich
 */
public class Block implements AutoCloseable {
    
    private final EphemeralStatementStorage ess;
    private final EphemeralQuerySolutionStorage eqss;
    
    public Block() {
        ess = new EphemeralStatementStorage();
        eqss = new EphemeralQuerySolutionStorage();
    }
    
    public EphemeralStatementStorage getEphemeralStatementStorage() {
        return ess;
    }

    public EphemeralQuerySolutionStorage getEphemeralQuerySolutionStorage() {
        return eqss;
    }
    
    @Override
    public void close() {
        ess.close();
        eqss.close();
    }
}
