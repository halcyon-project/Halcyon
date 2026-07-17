package com.ebremer.halcyon.utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StopWatch {
    private static final Logger logger = LoggerFactory.getLogger(StopWatch.class);

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
    logger.debug("{}", stat);
    return stat;
  }
  
  public String getLapseTime(String message) {
    String stat = "Elapsed: "+getElapsedTimeSecs()+" - "+message;
    logger.debug("{}", stat);
    this.startTime = System.nanoTime();
    return stat;
  }
}
