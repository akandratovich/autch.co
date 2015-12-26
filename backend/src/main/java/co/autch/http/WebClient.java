package co.autch.http;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import com.google.common.io.ByteStreams;

public class WebClient implements Closeable {
  private final CloseableHttpClient delegate;
  
  public WebClient() {
    ConnectionKeepAliveStrategy keepAlive = new ConnectionKeepAliveStrategy() {
      public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
        HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
        while (it.hasNext()) {
          HeaderElement he = it.nextElement();
          String param = he.getName();
          String value = he.getValue();
          if (value != null && param.equalsIgnoreCase("timeout")) {
            try {
              return Long.parseLong(value) * 1000;
            } catch (NumberFormatException ignore) {}
          }
        }
        return 30 * 1000;
      }
    };
    
    RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(10000).setConnectTimeout(10000).build();
    delegate = HttpClients.custom().setDefaultRequestConfig(config).setKeepAliveStrategy(keepAlive).build();
  }
  
  public byte[] getContent(String url) throws IOException {
    return getContent(url, Collections.emptyMap());
  }
  
  public byte[] getContent(String url, Map<String, String> query) throws IOException {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      getStream(url, query, output);
      return output.toByteArray();
    }
  }
  
  public void getStream(String url, OutputStream output) throws IOException {
    getStream(url, Collections.emptyMap(), output);
  }
  
  public void getStream(String url, Map<String, String> query, OutputStream output) throws IOException {
    RequestBuilder builder = RequestBuilder.get(url);
    for (String key : query.keySet()) {
      String value = query.get(key);
      builder.addParameter(key, value);
    }
    
    HttpUriRequest request = builder.build();
    
    try (CloseableHttpResponse response = delegate.execute(request)) {
      StatusLine line = response.getStatusLine();
      WebException.checkStatus(line);
      
      HttpEntity entity = response.getEntity();
      try (InputStream stream = entity.getContent()) {
        ByteStreams.copy(stream, output);
      }
    }
  }
  
  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
