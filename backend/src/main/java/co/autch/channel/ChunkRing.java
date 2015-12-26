package co.autch.channel;

import io.netty.buffer.ByteBuf;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChunkRing {
  private final AtomicReferenceArray<Chunk> data = new AtomicReferenceArray<>(RING_SIZE);
  
  public ChunkRing() {
    for (int i = 0; i < RING_SIZE; i++) {
      Chunk chunk = new Chunk();
      data.set(i, chunk);
    }
  }
  
  public Chunk at(long position) {
    int index = index(position);
    return data.get(index);
  }
  
  private int index(long position) {
    return (int) (position & RING_MASK);
  }
  
  public static class Chunk {
    private final ReadWriteLock lock   = new ReentrantReadWriteLock();
    private final Lock          rlock  = lock.readLock();
    private final Lock          wlock  = lock.writeLock();
    
    private final byte[]        buffer = new byte[1024 * 1024];
    private int                 length;
    
    public void read(ByteBuf target) {
      rlock.lock();
      try {
        target.writeBytes(buffer, 0, length);
      } finally {
        rlock.unlock();
      }
    }
    
    public void write(byte[] data) {
      wlock.lock();
      try {
        length = Math.min(buffer.length, data.length);
        System.arraycopy(data, 0, buffer, 0, length);
      } finally {
        wlock.unlock();
      }
    }
  }
  
  private static final int RING_SIZE = 1 << 3;
  private static final int RING_MASK = RING_SIZE - 1;
}
