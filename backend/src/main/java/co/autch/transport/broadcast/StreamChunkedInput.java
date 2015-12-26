package co.autch.transport.broadcast;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedInput;
import co.autch.channel.ChunkRing.Chunk;
import co.autch.channel.StreamContext;
import co.autch.channel.StreamOfflineException;

public class StreamChunkedInput implements ChunkedInput<ByteBuf> {
  private final StreamContext channel;
  private final String        format;
  
  private long                position;
  
  public StreamChunkedInput(StreamContext channel, String format) throws StreamOfflineException {
    this.channel = channel;
    this.format = format;
    
    this.position = channel.waitForPosition();
  }
  
  @Override
  public boolean isEndOfInput() {
    return !channel.online();
  }
  
  @Override
  public ByteBuf readChunk(ChannelHandlerContext ctx) {
    if (isEndOfInput()) {
      return null;
    }
    
    Chunk chunk = channel.tryChunk(format, position);
    if (chunk == null) {
      return null;
    }
    
    position++;
    
    ByteBuf buffer = ctx.alloc().buffer();
    chunk.read(buffer);
    
    return buffer;
  }
  
  @Override
  public void close() {}
}
