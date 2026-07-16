package com.ebremer.halcyon.lib.imaging;

import com.ebremer.halcyon.lib.Tile;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import org.locationtech.jts.geom.Polygon;

/**
 *
 * @author erich
 */
public class Filters {

    public static Predicate<Tile> DONOTHING() {
        return (Predicate<Tile>) (Tile tile) -> {
            return true;
        };
    }
    
    public static Predicate<Tile> Polygons(Polygon[] polygons) {
        return (Predicate<Tile>) (Tile tile) -> {
            Polygon region = tile.getTileRequest().getRegion().getPolygon();
            Optional<Polygon> contained = Arrays
            .stream(polygons)
            .parallel()
            .filter( p-> p.contains(region))
            .findFirst();
            return contained.isPresent();
        };
    }
    
    public static Predicate<Tile> NoBackground() {
        return (Predicate<Tile>) (Tile tile) -> {
            return true;
        };
    }
}
