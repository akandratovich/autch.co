package co.autch.util;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class Json {
  private static final ObjectMapper om = om();
  
  private Json() {}
  
  public static <T> T read(String json, Class<T> clazz) throws IOException {
    return om.readValue(json, clazz);
  }
  
  public static <T> T read(byte[] json, JavaType clazz) throws IOException {
    return om.readValue(json, clazz);
  }
  
  public static <T> T read(String json, JavaType clazz) throws IOException {
    return om.readValue(json, clazz);
  }
  
  public static <T> T read(InputStream is, Class<T> clazz) throws IOException {
    return om.readValue(is, clazz);
  }
  
  private static ObjectMapper om() {
    ObjectMapper om = new ObjectMapper();
    
    om.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    om.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    
    om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    
    return om;
  }
}
