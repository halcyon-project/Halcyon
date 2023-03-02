package com.ebremer.halcyon;

import java.awt.Polygon;
import java.util.LinkedList;
import org.davidmoten.hilbert.Range;

/**
 *
 * @author erich
 */
public class ExtendedPolygon {
    public int id = -1;
    public String classid = "";
    public int classidnum;
    public Polygon polygon;
    public Object neovalue;
    public LinkedList<Range> hsPolygon;
    public String raw;
}