package co.autch.transport.netty;

import co.autch.transport.broadcast.StreamBroadcaster;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

public class HttpStreamServerInitializer extends ChannelInitializer<SocketChannel> {
  private final StreamBroadcaster broadcaster;
  
  public HttpStreamServerInitializer(StreamBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
  }
  
  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();
    
    pipeline.addLast(new HttpServerCodec());
    pipeline.addLast(new HttpStreamServerHandler(broadcaster));
  }
}
