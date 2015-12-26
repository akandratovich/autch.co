package co.autch.channel;

import java.io.IOException;

public class StreamOfflineException extends IOException {
  private static final long serialVersionUID = 5568055703109034012L;
  
  private final String      channel;
  
  public StreamOfflineException(String channel) {
    super("Channel " + channel + " seems to be offline");
    this.channel = channel;
  }
  
  public String getChannel() {
    return channel;
  }
}
