package com.ebremer.halcyon.utils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.locationtech.jts.algorithm.hull.ConcaveHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 *
 * @author erich
 */
public class ConcaveHullExp {
    
    public static void main(String[] args) {
        int num = 128;
        int dim = 2000;
        int dimo = 1950;
        Random ran = new Random();
        Point point[] = new Point[num];
        GeometryFactory gf = new GeometryFactory();
        IntStream.range(0, num).forEach(i->{
            point[i] = gf.createPoint(new Coordinate(ran.nextDouble(50,dimo), ran.nextDouble(50, dimo)));
        });
        GeometryCollection gc = new GeometryCollection(point, gf);
        double threshold = 4;
        ConcaveHull cch = new ConcaveHull(gc);
        cch.setHolesAllowed(false);
        Geometry hull = cch.getHull();
        for (int i = 0; i < 5; i++) {
            System.out.println(hull.covers(point[i]));
        }
        for (Coordinate c: hull.getCoordinates()) {
            System.out.println(c);
        }
        
        Polygon polygon = new Polygon();
        for (Coordinate c : hull.getCoordinates()) {
            polygon.addPoint((int) c.x, (int) c.y);
        }
        int width = dim;
        int height = dim;
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLUE);
        g2d.drawPolygon(polygon);
        g2d.setColor(new Color(0, 0, 255, 50));
        g2d.fillPolygon(polygon);
        g2d.dispose();
        File file = new File("\\ATAN\\hullImage.png");
        try {
            ImageIO.write(bufferedImage, "PNG", file);
            System.out.println("Polygon image saved to " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        
        
    }
}
