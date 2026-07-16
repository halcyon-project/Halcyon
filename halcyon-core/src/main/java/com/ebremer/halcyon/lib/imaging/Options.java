package com.ebremer.halcyon.lib.imaging;

import static com.ebremer.halcyon.lib.imaging.Option.TissueOnly;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author erich
 */
public class Options {
    private final Set<Option> options;
    
    private Options() {
        this.options = new HashSet<>();
    }
    
    public static Options create() {
        return new Options();
    }
    
    public void empty() {
        options.clear();
    }
    
    public boolean contains(Option option) {
        return options.contains(option);
    }
    
    public Options OnlyTissue() {        
        options.add(TissueOnly);
        return this;
    }
}
