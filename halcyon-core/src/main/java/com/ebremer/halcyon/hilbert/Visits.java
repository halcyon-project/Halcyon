package com.ebremer.halcyon.hilbert;

import com.ebremer.halcyon.geometry.Point;
import java.util.HashMap;

/**
 *
 * @author erich
 */
public class Visits {
    private final HashMap<String,Integer> visits;
    
    public Visits() {
        visits = new HashMap<>();
    }
    
    public void visited(Point p) {
        String key = p.x+"#"+p.y;
        if (!visits.containsKey(key)) {
            visits.put(key, 1);
        } else {
            int count = visits.get(key);
            count++;
            visits.put(key, count);
        }
        //System.out.println("visited : "+(p.x-65536)+","+(p.y-28672)+" : "+visits.get(key));
    }
    
    public int getNumVisits(Point p) {
        String key = p.x+"#"+p.y;
        //System.out.println("getNumVisits : "+p.x+","+p.y);
        if (!visits.containsKey(key)) {
            //System.out.println("getNumVisits : "+(p.x-65536)+","+(p.y-28672)+"  returning ZERO");
            return 0;
        }
        //System.out.println("getNumVisits : "+(p.x-65536)+","+(p.y-28672)+"  returning "+visits.get(key));
        return visits.get(key);
    }
    
    public static void main(String[] args) {
        Visits ha = new Visits();
        Point a = new Point(122,213);
        Point b = new Point(122,213);
        Point c = new Point(22,113);
        ha.visited(a);
        ha.visited(a);
        ha.visited(a);
        ha.visited(a);
        ha.visited(a);
        ha.visited(b);
        ha.visited(b);
        ha.visited(b);
        ha.visited(b);
        
    }
}
