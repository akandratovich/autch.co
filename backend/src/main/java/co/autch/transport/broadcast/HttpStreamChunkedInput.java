package co.autch.transport.broadcast;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedInput;

public class HttpStreamChunkedInput implements ChunkedInput<HttpContent> {
  private final StreamChunkedInput input;
  private final LastHttpContent    lastHttpContent;
  private boolean                  sentLastChunk;
  
  public HttpStreamChunkedInput(StreamChunkedInput input) {
    this.input = input;
    this.lastHttpContent = LastHttpContent.EMPTY_LAST_CONTENT;
  }
  
  @Override
  public boolean isEndOfInput() {
    if (input.isEndOfInput()) {
      return sentLastChunk;
    } else {
      return false;
    }
  }
  
  @Override
  public void close() {
    input.close();
  }
  
  @Override
  public HttpContent readChunk(ChannelHandlerContext ctx) {
    if (input.isEndOfInput()) {
      if (sentLastChunk) {
        return null;
      } else {
        sentLastChunk = true;
        return lastHttpContent;
      }
    } else {
      ByteBuf buf = input.readChunk(ctx);
      if (buf == null) {
        return null;
      }
      
      return new DefaultHttpContent(buf);
    }
  }
}
