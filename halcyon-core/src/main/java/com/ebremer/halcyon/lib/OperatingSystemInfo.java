package com.ebremer.halcyon.lib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperatingSystemInfo {
    private static final Logger logger = LoggerFactory.getLogger(OperatingSystemInfo.class);
       
    public static String getName() {
        return System.getProperty("os.name");
    }
    
    public static String getVersion() {
        return System.getProperty("os.version");    
    }
    
    public static String getArchitecture() {
        return System.getProperty("os.arch");        
    }
    
    public static boolean ifWindows() {
        return getName().startsWith("Windows");
    }
    
    public static boolean ifMac() {
        return getName().startsWith("Mac");
    }
    
    public static boolean ifLinux() {
        return getName().startsWith("Linux");
    }

    public static void main(String[] args) {
        logger.debug("Operating System Name: {}", OperatingSystemInfo.getName());
        logger.debug("Operating System Version: {}", OperatingSystemInfo.getVersion());
        logger.debug("Operating System Architecture: {}", OperatingSystemInfo.getArchitecture());
    }
}
