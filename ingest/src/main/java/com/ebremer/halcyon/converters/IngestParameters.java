package com.ebremer.halcyon.converters;

import com.beust.jcommander.Parameter;
import java.io.File;

/**
 *
 * @author erich
 */
public class IngestParameters {
    @Parameter(names = "-help", converter = BooleanConverter.class, help = true)
    private boolean help;
    
    public boolean isHelp() {
        return help;
    }
    
    @Parameter(names = "-src", description = "Source Folder or File", required = true)
    public File src;

    @Parameter(names = "-dest", description = "Destination Folder or File", required = true)
    public File dest;
    
    @Parameter(names = {"-heatmap"}, converter = BooleanConverter.class)
    public boolean optimize = false;
    
    @Parameter(names = {"-version","-v"}, converter = BooleanConverter.class)
    public boolean version = false;

    @Parameter(names = "-threads", description = "# of Threads")
    public int threads = 1;
}
