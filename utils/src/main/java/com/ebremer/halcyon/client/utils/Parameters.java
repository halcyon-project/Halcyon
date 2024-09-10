package com.ebremer.halcyon.client.utils;

import com.beust.jcommander.Parameter;
import java.io.File;
import java.net.URI;

/**
 *
 * @author erich
 */
public class Parameters {  

    @Parameter(names = {"-src","-s"}, converter = URIConverter.class, description = "Source URI", required = true, validateWith = UtilsValidator.class, order = 0)
    public URI src;
    
    @Parameter(names = {"-dest","-d"}, converter = FileConverter.class, description = "Destination Folder or File", required = false, validateWith = UtilsValidator.class, order = 1)
    public File dest = null;  

    @Parameter(names = {"-threads","-t"}, converter = IntegerConverter.class, description = "# of threads for concurrent downloading", validateWith = PositiveInteger.class, order = 2)
    public int threads = 1;  
    
    @Parameter(names = {"-version","-v"}, converter = BooleanConverter.class, validateWith = UtilsValidator.class, order = 3)
    public Boolean version = false;
    
    @Parameter(names = {"-help","-h"}, converter = BooleanConverter.class, description = "Display help information", validateWith = UtilsValidator.class, order = 4)
    public Boolean help = false;
    
}
