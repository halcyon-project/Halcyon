/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import com.ebremer.ethereal.Solution;
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