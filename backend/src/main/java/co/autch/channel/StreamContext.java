package co.autch.channel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import co.autch.channel.ChunkRing.Chunk;
import co.autch.codec.StreamCodec;

import com.google.common.collect.Maps;

public class StreamContext implements AutoCloseable {
  private final String                  channel;
  
  private final String                  location;
  private final String                  subpath;
  
  private final Collection<StreamCodec> chain;
  
  private final Map<String, ChunkRing>  data   = Maps.newHashMap();
  private final AtomicLong              cursor = new AtomicLong(-1);
  
  public StreamContext(String channel, String location) {
    this.channel = channel;
    this.location = location;
    
    int i = location.lastIndexOf("/");
    this.subpath = location.substring(0, i + 1);
    
    for (String name : StreamCodec.supported()) {
      data.put(name, new ChunkRing());
    }
    
    this.chain = StreamCodec.initialize(channel);
  }
  
  void decode(Path source, long position) throws Exception {
    Path current = source;
    for (StreamCodec codec : chain) {
      codec.decode(current);
      current = codec.path();
      
      byte[] content = Files.readAllBytes(current);
      
      Chunk chunk = chunk(codec.name(), position);
      chunk.write(content);
    }
    
    commit(position);
  }
  
  String location() {
    return location;
  }
  
  String subpath() {
    return subpath;
  }
  
  public String channel() {
    return channel;
  }
  
  Chunk chunk(String name, long position) {
    return data.get(name).at(position);
  }
  
  long cursor() {
    return cursor.get();
  }
  
  void commit(long position) {
    cursor.set(position);
  }
  
  void fail() {
    cursor.set(OFFLINE_POSITION);
  }
  
  public Chunk tryChunk(String format, long position) {
    if (cursor() < position) {
      return null;
    }
    
    return data.get(format).at(position);
  }
  
  public boolean online() {
    return cursor() != OFFLINE_POSITION;
  }
  
  public long waitForPosition() throws StreamOfflineException {
    return waitForPosition(0);
  }
  
  private long waitForPosition(long position) throws StreamOfflineException {
    while (true) {
      long c = cursor();
      
      if (c >= position) {
        return c;
      }
      
      if (c == OFFLINE_POSITION) {
        throw new StreamOfflineException(channel);
      }
      
      LockSupport.parkNanos(SLEEP);
    }
  }
  
  @Override
  public void close() {
    for (StreamCodec codec : chain) {
      codec.close();
    }
  }
  
  @Override
  public String toString() {
    return "StreamContext [channel=" + channel + "]";
  }
  
  private static final long OFFLINE_POSITION = -2;
  private static final long SLEEP            = TimeUnit.MILLISECONDS.toNanos(100);
}
