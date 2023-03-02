package com.ebremer.halcyon.utils;

public class StopWatch {

private long startTime = 0;
private long stopTime = 0;
private boolean running = false;

public StopWatch(boolean start) {
    if (start) {
        start();
    }
}

  public StopWatch start() {
    this.startTime = System.nanoTime();
    this.running = true;
    return this;
  }


  public StopWatch stop() {
    this.stopTime = System.nanoTime();
    this.running = false;
    return this;
  }
  
  public StopWatch reset() {
    this.startTime = System.nanoTime();
    this.running = true;      
    return this;
  }

  public long getElapsedTime() {
    long elapsed;
    if (running) {
      elapsed = (System.nanoTime() - startTime);
    } else {
      elapsed = (stopTime - startTime);
    }
    return elapsed;
  }

  public double getElapsedTimeSecs() {
    double elapsed;
    if (running) {
      elapsed = (((double) (System.nanoTime() - startTime)) / 1000000000d);
    } else {
      elapsed = (((double) (stopTime - startTime)) / 1000000000D);
    }
    return elapsed;
  }

  public String getTime(String message) {
    String stat = "Elapsed: "+getElapsedTimeSecs()+" - "+message;
    System.out.println(stat);
    return stat;
  }
  
  public StopWatch getTime() {
    System.out.println("Elapsed: "+getElapsedTimeSecs());
    return this;
  }
}