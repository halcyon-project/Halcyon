/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
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
