package com.ebremer.halcyon.puffin;

/**
 *
 * @author erich
 */
public class Block implements AutoCloseable {
    
    private final EphemeralStatementStorage ess;
    private final EphemeralQuerySolutionStorage eqss;
    private final EphemeralResourceStorage ers;
    private final EphemeralModelStorage ems;
    
    public Block() {
        ess = new EphemeralStatementStorage();
        eqss = new EphemeralQuerySolutionStorage();
        ers = new EphemeralResourceStorage();
        ems = new EphemeralModelStorage();
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
    
    public EphemeralModelStorage getEphemeralModelStorage() {
        return ems;
    }
    
    @Override
    public void close() {
        ess.close();
        eqss.close();
        ers.close();
        ems.close();          
    }
}
