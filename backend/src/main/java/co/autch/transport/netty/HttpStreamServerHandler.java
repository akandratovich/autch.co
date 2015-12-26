package co.autch.transport.netty;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.autch.channel.StreamOfflineException;
import co.autch.codec.StreamCodec;
import co.autch.http.WebException;
import co.autch.transport.broadcast.StreamBroadcaster;

public class HttpStreamServerHandler extends SimpleChannelInboundHandler<DefaultHttpRequest> {
  private final StreamBroadcaster broadcaster;
  
  public HttpStreamServerHandler(StreamBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
  }
  
  @Override
  protected void channelRead0(ChannelHandlerContext ctx, DefaultHttpRequest request) throws Exception {
    if (!request.getDecoderResult().isSuccess()) {
      sendError(ctx, BAD_REQUEST);
      return;
    }
    
    if (request.getMethod() != GET) {
      sendError(ctx, METHOD_NOT_ALLOWED);
      return;
    }
    
    String uri = request.getUri();
    String channel = parseChannel(uri);
    if (channel == null) {
      sendError(ctx, NOT_FOUND);
      return;
    }
    
    String name = parseFormat(uri);
    if (name == null) {
      sendError(ctx, UNSUPPORTED_MEDIA_TYPE);
      return;
    }
    
    try {
      broadcaster.subscribe(ctx, channel, name);
    } catch (WebException e) {
      sendError(ctx, e.getStatus());
    } catch (StreamOfflineException e) {
      sendError(ctx, NOT_FOUND);
    } catch (Exception e) {
      logger.warn("Error while initializing channel({})", channel, e);
      sendError(ctx, INTERNAL_SERVER_ERROR);
    }
  }
  
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    logger.warn("Exception caught {}", ctx.channel(), cause);
    ctx.channel().close();
  }
  
  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ctx.channel().close();
  }
  
  private static final Pattern PATTERN = Pattern.compile("[a-zA-Z0-9-_]+");
  
  private static String parseChannel(String uri) {
    if (uri.isEmpty() || uri.charAt(0) != '/') {
      return null;
    }
    
    String path = uri.substring(1);
    int index = path.lastIndexOf(".");
    if (index < 0) {
      index = path.length();
    }
    
    String channel = path.substring(0, index);
    if (!PATTERN.matcher(channel).matches()) {
      return null;
    }
    
    return channel;
  }
  
  private String parseFormat(String uri) {
    int index = uri.lastIndexOf(".") + 1;
    String name = uri.substring(index);
    if (StreamCodec.supported(name)) {
      return name;
    }
    
    return "aac";
  }
  
  private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
    response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
    
    // Close the connection as soon as the error message is sent.
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
  }
  
  private static final Logger logger = LoggerFactory.getLogger(HttpStreamServerHandler.class);
}
