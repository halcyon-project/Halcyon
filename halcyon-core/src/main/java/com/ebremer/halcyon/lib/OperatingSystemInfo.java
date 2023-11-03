package com.ebremer.halcyon.lib;

public class OperatingSystemInfo {
       
    public static String getName() {
        return System.getProperty("os.name");
    }
    
    public static String getVersion() {
        return System.getProperty("os.version");    
    }
    
    public static String getArchitecture() {
        return System.getProperty("os.arch");        
    }

    public static void main(String[] args) {
        System.out.println("Operating System Name: " + OperatingSystemInfo.getName());
        System.out.println("Operating System Version: " + OperatingSystemInfo.getVersion());
        System.out.println("Operating System Architecture: " + OperatingSystemInfo.getArchitecture());
    }
}
