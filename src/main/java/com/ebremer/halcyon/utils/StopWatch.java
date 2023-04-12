package com.ebremer.halcyon.utils;

public final class StopWatch {

private long startTime = 0;
private long stopTime = 0;
private boolean running = false;

public StopWatch() {
    start();
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

  private double getElapsedTimeSecs() {
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
}
