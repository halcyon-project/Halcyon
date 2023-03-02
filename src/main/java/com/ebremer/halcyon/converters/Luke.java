/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.converters;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 *
 * @author erich
 */
public class Luke {
    
    public static void main(String[] args) {
        int px = 224;
        int py = 224;
        try(Scanner scanner = new Scanner(new File("D:\\luke\\cluster_feature.csv"))){
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                try (Scanner rowScanner = new Scanner(line)) {
                    rowScanner.useDelimiter(",");
                    String path = rowScanner.next();
                    String clazz = rowScanner.next();
                    if (path.startsWith("/data04/shared/skapse/Luke/Datasets/TCGA_PAAD_10X/single/TCGA-2J-AAB1-01Z-00-DX1")) {
                        System.out.println(path+" ====> "+clazz);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }   
    }
}
