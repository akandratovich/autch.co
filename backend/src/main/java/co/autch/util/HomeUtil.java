package co.autch.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class HomeUtil {
  private static final Path home = Paths.get(System.getProperty("user.home"), ".autch");
  
  private HomeUtil() {}
  
  public static Path path(String... name) {
    Path path = home;
    for (String part : name) {
      path = path.resolve(part);
    }
    
    path.getParent().toFile().mkdirs();
    return path;
  }
}
