package co.autch;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.autch.transport.netty.HttpStreamServer;

import com.google.common.primitives.Ints;

public class App {
  private static final int DEFAULT_PORT = 22000;
  
  public static void main(String[] args) throws Exception {
    int port = getPort(args);
    
    logger.info("Starting http server at port {}", port);
    CountDownLatch latch = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Shutting down http server");
        latch.countDown();
      }
    });
    
    try (HttpStreamServer server = new HttpStreamServer(port)) {
      latch.await();
    }
  }
  
  private static int getPort(String[] args) {
    if (args.length == 0) {
      return DEFAULT_PORT;
    }
    
    String value = args[0];
    Integer port = Ints.tryParse(value);
    if (port == null) {
      return DEFAULT_PORT;
    }
    
    return port;
  }
  
  private static final Logger logger = LoggerFactory.getLogger(App.class);
}
