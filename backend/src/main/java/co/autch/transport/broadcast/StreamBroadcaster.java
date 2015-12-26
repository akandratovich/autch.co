package co.autch.transport.broadcast;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;

import java.io.Closeable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.autch.channel.StreamContext;
import co.autch.channel.StreamManager;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class StreamBroadcaster implements Closeable {
  private final BlockingQueue<ChannelSubscription> queue   = new LinkedBlockingQueue<>();
  private final AtomicBoolean                      working = new AtomicBoolean(true);
  
  private final StreamManager                      manager;
  
  public StreamBroadcaster(StreamManager manager) {
    this.manager = manager;
    
    ThreadFactory tf = new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat("twitch-broadcaster-%d")
        .build();
    
    ExecutorService service = Executors.newFixedThreadPool(16, tf);
    for (int i = 0; i < 16; i++) {
      service.submit(() -> loop());
    }
    service.shutdown();
  }
  
  public void subscribe(ChannelHandlerContext ctx, String stream, String format) throws Exception {
    ChannelSubscription subscription = new ChannelSubscription(ctx, stream, format);
    
    HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
    response.headers().set(CONTENT_TYPE, "audio/" + format);
    response.headers().set(TRANSFER_ENCODING, CHUNKED);
    ctx.write(response);
    
    queue.add(subscription);
  }
  
  private void loop() {
    try {
      while (working.get()) {
        ChannelSubscription subscription = queue.poll();
        if (subscription == null) {
          LockSupport.parkNanos(SLEEP_NANOS);
          continue;
        }
        
        ChannelHandlerContext context = subscription.context();
        Channel channel = context.channel();
        
        if (!channel.isActive()) {
          logger.info("Channel is not active {}, close subscription", channel);
          
          subscription.close();
          continue;
        }
        
        try {
          if (!channel.isWritable()) {
            continue;
          }
          
          ChunkData chunk = subscription.read();
          
          if (chunk.suspend()) {
            continue;
          }
          
          Object message = chunk.chunk();
          if (message == null) {
            message = Unpooled.EMPTY_BUFFER;
          }
          
          ChannelFuture f = channel.write(message);
          f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
              // TODO ??? multiple close can be
              if (!future.isSuccess() || chunk.endOfInput()) {
                logger.info("Close subscription (fail {}, eof {})", !future.isSuccess(), chunk.endOfInput());
                if (!future.isSuccess()) {
                  logger.warn("Fail cause", future.cause());
                  future.cause().printStackTrace();
                }
                
                subscription.close();
              }
            }
          });
          
          channel.flush();
        } finally {
          queue.add(subscription);
        }
      }
    } catch (Exception e) {
      logger.warn("Failed to initialize broadcaster thread", e);
    }
  }
  
  @Override
  public void close() {
    working.set(false);
  }
  
  private class ChannelSubscription implements Closeable {
    private final ChannelHandlerContext  ctx;
    private final String                 stream;
    
    private final String                 uuid;
    private final HttpStreamChunkedInput input;
    
    public ChannelSubscription(ChannelHandlerContext ctx, String stream, String format) throws Exception {
      this.ctx = ctx;
      this.stream = stream;
      
      this.uuid = ctx.channel().remoteAddress().toString();
      
      StreamContext context = manager.acquire(stream, uuid);
      
      try {
        StreamChunkedInput chunked = new StreamChunkedInput(context, format);
        this.input = new HttpStreamChunkedInput(chunked);
      } catch (Exception e) {
        manager.release(stream, uuid);
        throw e;
      }
    }
    
    public ChannelHandlerContext context() {
      return ctx;
    }
    
    public ChunkData read() throws Exception {
      return new ChunkData(input, ctx);
    }
    
    @Override
    public void close() {
      input.close();
      ctx.close();
      
      manager.release(stream, uuid);
    }
  }
  
  private class ChunkData {
    private final HttpContent chunk;
    private final boolean     endOfInput;
    
    public ChunkData(HttpStreamChunkedInput input, ChannelHandlerContext ctx) {
      this.chunk = input.readChunk(ctx);
      this.endOfInput = input.isEndOfInput();
    }
    
    public HttpContent chunk() {
      return chunk;
    }
    
    public boolean endOfInput() {
      return endOfInput;
    }
    
    public boolean suspend() {
      return chunk == null && !endOfInput;
    }
  }
  
  private static final long   SLEEP_NANOS = TimeUnit.MILLISECONDS.toNanos(300);
  
  private static final Logger logger      = LoggerFactory.getLogger(StreamBroadcaster.class);
}
