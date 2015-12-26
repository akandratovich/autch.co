package co.autch.transport.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.Closeable;

import co.autch.channel.StreamManager;
import co.autch.transport.broadcast.StreamBroadcaster;

public final class HttpStreamServer implements Closeable {
  private final EventLoopGroup    bossGroup   = new NioEventLoopGroup(1);
  private final EventLoopGroup    workerGroup = new NioEventLoopGroup();
  
  private final StreamManager     manager     = new StreamManager();
  private final StreamBroadcaster broadcaster = new StreamBroadcaster(manager);
  
  private final Channel           channel;
  
  public HttpStreamServer(int port) throws Exception {
    try {
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
          .option(ChannelOption.TCP_NODELAY, true)
          .option(ChannelOption.SO_BACKLOG, 1024)
          .channel(NioServerSocketChannel.class)
          .childHandler(new HttpStreamServerInitializer(broadcaster));
      
      channel = b.bind(port).sync().channel();
    } catch (Exception e) {
      close();
      throw e;
    }
  }
  
  @Override
  public void close() {
    broadcaster.close();
    manager.close();
    
    try {
      if (channel != null) {
        channel.closeFuture().sync();
      }
    } catch (InterruptedException e) {}
    
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
  }
}
