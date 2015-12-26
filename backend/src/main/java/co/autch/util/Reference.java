package co.autch.util;

import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public class Reference<T> {
  private final ConcurrentMap<String, Long> refs = Maps.newConcurrentMap();
  private final T                           value;
  
  public Reference(T value) {
    this.value = value;
  }
  
  public T acquire(String uuid) {
    if (refs.putIfAbsent(uuid, System.currentTimeMillis()) != null) {
      logger.warn("{} - multiple reference({}) acquire", uuid, value);
      return value;
    }
    
    logger.info("{} acquired reference({}), total {}", uuid, value, refs.size());
    return value;
  }
  
  public void release(String uuid) {
    Long mark = refs.remove(uuid);
    if (mark == null) {
      logger.warn("{} - multiple reference({}) release", uuid, value);
      return;
    }
    
    long period = System.currentTimeMillis() - mark;
    logger.info("{} released reference({}) after {} ms, total {}", uuid, value, period, refs.size());
  }
  
  public boolean free() {
    return refs.isEmpty();
  }
  
  private static final Logger logger = LoggerFactory.getLogger(Reference.class);
}
