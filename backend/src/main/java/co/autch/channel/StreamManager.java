package co.autch.channel;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.autch.http.WebClient;
import co.autch.util.HomeUtil;
import co.autch.util.PlaylistSupport;
import co.autch.util.Reference;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class StreamManager implements Closeable {
  private final PlaylistSupport                                 support = new PlaylistSupport();
  private final WebClient                                       http    = new WebClient();
  private final AtomicBoolean                                   working = new AtomicBoolean(true);
  
  private final BlockingQueue<StreamContext>                    queue   = new LinkedBlockingQueue<>();
  private final ConcurrentMap<String, Reference<StreamContext>> active  = Maps.newConcurrentMap();
  
  public StreamManager() {
    ThreadFactory tf = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("twitch-worker-%d")
        .build();
    
    ExecutorService service = Executors.newFixedThreadPool(32, tf);
    for (int i = 0; i < 32; i++) {
      service.submit(() -> loop());
    }
    service.shutdown();
  }
  
  public synchronized StreamContext acquire(String name, String uuid) throws Exception {
    Reference<StreamContext> ref = active.get(name);
    if (ref != null) {
      return ref.acquire(uuid);
    }
    
    String stream = support.stream(http, name);
    StreamContext info = new StreamContext(name, stream);
    
    Reference<StreamContext> reference = new Reference<StreamContext>(info);
    StreamContext data = reference.acquire(uuid);
    
    logger.info("Activate channel({})", name);
    
    active.put(name, reference);
    queue.add(info);
    
    return data;
  }
  
  public synchronized void release(String channel, String uuid) {
    Reference<StreamContext> reference = active.get(channel);
    if (reference == null) {
      logger.warn("Attempt to release unknown channel({}) from uuid({})", channel, uuid);
      return;
    }
    
    reference.release(uuid);
  }
  
  private void loop() {
    WebClient client = new WebClient();
    
    try {
      String filename = "chunk-" + Thread.currentThread().getId() + ".mpegts";
      Path mpegtsPath = HomeUtil.path("hls", filename);
      
      while (working.get()) {
        StreamContext info = queue.poll();
        if (info == null) {
          LockSupport.parkNanos(SLEEP_NANOS);
          continue;
        }
        
        Reference<StreamContext> reference = active.get(info.channel());
        if (reference.free()) {
          logger.info("Shutdown channel({})", info.channel());
          
          active.remove(info.channel());
          info.close();
          
          continue;
        }
        
        try {
          String location = info.location();
          Collection<String> data = support.track(client, location);
          for (String path : data) {
            String chunkPosition = path.substring(6, 16);
            long position = Integer.parseInt(chunkPosition);
            
            if (info.cursor() >= position) {
              continue;
            }
            
            try (OutputStream output = Files.newOutputStream(mpegtsPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
              String uri = info.subpath() + path;
              client.getStream(uri, output);
            }
            
            info.decode(mpegtsPath, position);
          }
        } catch (Exception e) {
          logger.warn("Exception while process channel({})", info.channel(), e);
          info.fail();
        } finally {
          queue.add(info);
        }
      }
    } finally {
      try {
        client.close();
      } catch (IOException e) {
        logger.warn("Failed to close http client gracefully", e);
      }
    }
  }
  
  @Override
  public void close() {
    working.set(false);
    
    try {
      http.close();
    } catch (IOException e) {
      logger.warn("Failed to close http client gracefully", e);
    }
  }
  
  private static final long   SLEEP_NANOS = TimeUnit.MILLISECONDS.toNanos(300);
  
  private static final Logger logger      = LoggerFactory.getLogger(StreamManager.class);
}
