package com.ebremer.halycon.jumbf;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author Erich Bremer
 */
public class Test {
    
    public static void main(String[] args) throws IOException {
        File file = new File("e:\\jumbf\\test-20260203-ed25519.jpg");
      //  File file2 = new File("e:\\jumbf\\THEBOYS.jpg");
        //IO.println(JUMBFUtils.containsJUMBF(file2));
        byte[] jumbf = JUMBFUtils.getJUMBF(file);
      //  IO.println(JUMBFUtils.hexDump(jumbf));
        IO.println("=============");
        IO.println(JUMBFUtils.containsJUMBF(file));
        String type = JUMBFParser.getFirstPayloadType(jumbf);
        System.out.println("JUMBF format type: " + type);
        IO.println("=============");
        IO.println("Contains JSON : "+JUMBFJsonExtractor.containsJsonPayload(file));
        String c2paJson = JUMBFJsonExtractor.getJsonPayload(file);
        IO.println(c2paJson);
        JUMBFJsonExtractor.DumpBinary(file, new File("e:\\jumbf"));
    }
}
