package co.autch.http;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;

import org.apache.http.StatusLine;

public class WebException extends IOException {
  private static final long serialVersionUID = -622242207261732597L;
  
  private final int         code;
  private final String      phrase;
  
  public WebException(StatusLine line) {
    super("Http response status " + line.getStatusCode());
    
    this.code = line.getStatusCode();
    this.phrase = line.getReasonPhrase();
  }
  
  public HttpResponseStatus getStatus() {
    return new HttpResponseStatus(code, phrase);
  }
  
  public static void checkStatus(StatusLine line) throws WebException {
    if (line.getStatusCode() != 200) {
      throw new WebException(line);
    }
  }
}
