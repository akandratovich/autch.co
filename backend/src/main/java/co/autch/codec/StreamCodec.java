package co.autch.codec;

import io.humble.video.DemuxerFormat;
import io.humble.video.MuxerFormat;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import co.autch.util.HomeUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public abstract class StreamCodec implements Closeable {
  private static final Set<String> SUPPORTED     = Sets.newHashSet("aac", "mpeg");
  
  private final DemuxerFormat      demuxerFormat = DemuxerFormat.findFormat("mpegts");
  private final MuxerFormat        muxerFormat;
  private final Path               path;
  
  protected StreamCodec(String stream, MuxerFormat muxerFormat) {
    this.muxerFormat = muxerFormat;
    this.path = HomeUtil.path("codec", stream, "chunk." + name());
  }
  
  public abstract void decode(Path source) throws Exception;
  
  public abstract String name();
  
  public Path path() {
    return path;
  }
  
  protected DemuxerFormat demuxerFormat() {
    return demuxerFormat;
  }
  
  public MuxerFormat muxerFormat() {
    return muxerFormat;
  }
  
  public static Collection<StreamCodec> initialize(String stream) {
    return Lists.newArrayList(new AacStreamExtractor(stream), new AacToMpegStreamCodec(stream));
  }
  
  public static boolean supported(String name) {
    return SUPPORTED.contains(name);
  }
  
  public static Collection<String> supported() {
    return SUPPORTED;
  }
  
  @Override
  public void close() {
    demuxerFormat.delete();
    muxerFormat.delete();
    
    try {
      Files.delete(path);
    } catch (IOException e) {}
  }
}
