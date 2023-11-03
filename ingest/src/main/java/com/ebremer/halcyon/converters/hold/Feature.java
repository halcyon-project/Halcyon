package com.ebremer.halcyon.converters.hold;

/**
 *
 * @author erich
 */
public class Feature implements Comparable<Feature> {     
private final int id;
private final long si;

public Feature(int id, long si) {
    this.id = id;
    this.si = si;
}

public int getid() {         
    return id;
}       

public long getsi() {
    return si;
}

@Override
public int compareTo(Feature other) {
    return (this.getsi() < other.getsi() ? -1 : (this.getsi() == other.getsi() ? 0 : 1));
}

@Override
public String toString() {
    return " ID: " + this.id + ", SI: " + this.si;
}
}