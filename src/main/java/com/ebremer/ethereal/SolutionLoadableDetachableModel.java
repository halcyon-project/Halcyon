package com.ebremer.ethereal;

import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class SolutionLoadableDetachableModel extends LoadableDetachableModel<Solution> {
    private final Solution solution;

    public SolutionLoadableDetachableModel(Solution m) {
        super();
        this.solution = m;
    }
     
    public void flush() {
        
    }

    @Override
    public Solution load() {
        return solution;
    }
}