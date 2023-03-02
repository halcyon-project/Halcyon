package com.ebremer.halcyon.utils;

import java.io.File;
import java.io.FilenameFilter;

/**
 *
 * @author erich
 */
public class ListFiles {
    private String FILE_DIR;
    private String FILE_TEXT_EXT;

    public ListFiles() {
        this.FILE_DIR = null;
        this.FILE_TEXT_EXT = null;
    }
    
    public String[] listFile(String folder, String ext) {
        FILE_DIR = folder;
        FILE_TEXT_EXT = ext;
	GenericExtFilter filter = new GenericExtFilter(ext);
	File dir = new File(folder);
	String[] list = dir.list(filter);
        String[] fullpath = new String[list.length];
	for (int i=0; i<list.length; i++) {
            fullpath[i] = new StringBuffer(FILE_DIR).append(File.separator).append(list[i]).toString();
	}
        return fullpath;
    }
    
    public String[] listFile(File dir, String ext) {
        FILE_TEXT_EXT = ext;
	GenericExtFilter filter = new GenericExtFilter(ext);
	String[] list = dir.list(filter);
        String[] fullpath = new String[list.length];
	for (int i=0; i<list.length; i++) {
            fullpath[i] = new StringBuffer(dir.getAbsolutePath()).append(File.separator).append(list[i]).toString();
	}
        return fullpath;
    }
    
    public class GenericExtFilter implements FilenameFilter {
	private final String ext;
        public GenericExtFilter(String ext) {
            this.ext = ext;
        }
        @Override
        public boolean accept(File dir, String name) {
            return (name.endsWith(ext));
        }
    }
}