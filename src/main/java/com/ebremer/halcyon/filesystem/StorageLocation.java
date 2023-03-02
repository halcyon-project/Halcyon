/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.filesystem;

import java.nio.file.Path;

/**
 *
 * @author erich
 */
public class StorageLocation {
    public Path path;
    public String urlpath;
    
    public StorageLocation(Path path, String urlpath) {
        this.path = path;
        this.urlpath = urlpath;
    }
}
