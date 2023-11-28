package com.ebremer.ethereal;

import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 * @param <Solution>
 */
public class ISolutionModel<Solution> implements IModel<Solution> {
    Solution solution;
    
    public ISolutionModel(Solution s) {
        this.solution = s;
    }

    @Override
    public Solution getObject() {
        return solution;
    }
}
