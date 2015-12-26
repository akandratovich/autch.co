package co.autch.codec;

import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerStream;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;

import java.io.IOException;
import java.nio.file.Path;

public class AacStreamExtractor extends StreamCodec {
  public AacStreamExtractor(String stream) {
    super(stream, MuxerFormat.guessFormat("adts", null, "audio/aac"));
  }
  
  @Override
  public String name() {
    return "aac";
  }
  
  @Override
  public void decode(Path source) throws Exception {
    Demuxer demuxer = Demuxer.make();
    demuxer.open(source.toString(), demuxerFormat(), false, true, null, null);
    
    AudioStreamInfo stream = selectStream(demuxer);
    
    Decoder decoder = stream.decoder();
    decoder.open(null, null);
    
    Muxer muxer = Muxer.make(path().toString(), muxerFormat(), null);
    muxer.addNewStream(decoder);
    muxer.open(null, null);
    
    try {
      MediaPacket packet = MediaPacket.make();
      
      int id = stream.id();
      while (demuxer.read(packet) >= 0) {
        if (packet.getStreamIndex() != id) {
          continue;
        }
        
        if (!packet.isComplete()) {
          continue;
        }
        
        muxer.write(packet, true);
      }
    } finally {
      demuxer.close();
      muxer.close();
    }
  }
  
  @Override
  public void close() {
    super.close();
  }
  
  protected AudioStreamInfo selectStream(Demuxer demuxer) throws InterruptedException, IOException {
    int numStreams = demuxer.getNumStreams();
    for (int i = 0; i < numStreams; i++) {
      DemuxerStream stream = demuxer.getStream(i);
      Decoder decoder = stream.getDecoder();
      if (decoder != null && decoder.getCodecType() == MediaDescriptor.Type.MEDIA_AUDIO) {
        return new AudioStreamInfo(i, decoder);
      }
    }
    
    throw new RuntimeException("Could not find audio stream in container: " + demuxer.getURL());
  }
  
  protected class AudioStreamInfo {
    private final Decoder decoder;
    private final int     id;
    
    public AudioStreamInfo(int id, Decoder decoder) {
      this.id = id;
      this.decoder = decoder;
    }
    
    public Decoder decoder() {
      return decoder;
    }
    
    public int id() {
      return id;
    }
  }
}
