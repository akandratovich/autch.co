package co.autch.codec;

import io.humble.video.AudioChannel;
import io.humble.video.Codec;
import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerStream;
import io.humble.video.Encoder;
import io.humble.video.MediaAudio;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;

import java.io.IOException;
import java.nio.file.Path;

public abstract class AbstractStreamCodec extends StreamCodec {
  private final MediaPacket readPacket  = MediaPacket.make(10240);
  private final MediaPacket writePacket = MediaPacket.make(10240);
  
  public AbstractStreamCodec(String stream, MuxerFormat muxerFormat) {
    super(stream, muxerFormat);
  }
  
  @Override
  public void decode(Path source) throws Exception {
    Demuxer demuxer = Demuxer.make();
    demuxer.open(source.toString(), demuxerFormat(), false, true, null, null);
    
    AudioStreamInfo stream = selectStream(demuxer);
    
    Decoder decoder = stream.decoder();
    decoder.open(null, null);
    
    Encoder encoder = createEncoder();
    encoder.open(null, null);
    
    Muxer muxer = Muxer.make(path().toString(), muxerFormat(), null);
    muxer.addNewStream(encoder);
    muxer.open(null, null);
    
    try (StreamResampler resampler = new StreamResampler(decoder, encoder)) {
      MediaAudio input = resampler.getInput();
      MediaAudio output = resampler.getOutput();
      
      while (demuxer.read(readPacket) >= 0) {
        if (readPacket.getStreamIndex() != stream.id()) {
          continue;
        }
        
        if (!readPacket.isComplete()) {
          continue;
        }
        
        int offset = 0;
        do {
          offset += decoder.decode(input, readPacket, offset);
          convert(muxer, encoder, resampler, input, output);
        } while (offset < readPacket.getSize());
      }
      
      do {
        decoder.decode(input, null, 0);
        convert(muxer, encoder, resampler, input, output);
      } while (input.isComplete());
      
      do {
        encoder.encode(writePacket, null);
        write(muxer);
      } while (writePacket.isComplete());
    } finally {
      demuxer.close();
      muxer.close();
    }
  }
  
  private Encoder createEncoder() {
    Codec encodec = Codec.findEncodingCodec(muxerFormat().getDefaultAudioCodecId());
    
    Encoder encoder = Encoder.make(encodec);
    encoder.setChannelLayout(AudioChannel.Layout.CH_LAYOUT_STEREO);
    encoder.setSampleFormat(encodec.getSupportedAudioFormat(0));
    encoder.setSampleRate(44100);
    if (muxerFormat().getFlag(MuxerFormat.Flag.GLOBAL_HEADER)) {
      encoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);
    }
    
    return encoder;
  }
  
  private void convert(Muxer muxer, Encoder encoder, StreamResampler resampler, MediaAudio input, MediaAudio output) {
    if (input.isComplete()) {
      resampler.resample();
      encoder.encodeAudio(writePacket, output);
      
      write(muxer);
    }
  }
  
  private void write(Muxer muxer) {
    if (writePacket.isComplete()) {
      muxer.write(writePacket, false);
    }
  }
  
  @Override
  public void close() {
    super.close();
    
    readPacket.delete();
    writePacket.delete();
  }
  
  protected AudioStreamInfo selectStream(Demuxer demuxer) throws InterruptedException, IOException {
    int numStreams = demuxer.getNumStreams();
    int audioStreamId = -1;
    Decoder audioDecoder = null;
    for (int i = 0; i < numStreams; i++) {
      DemuxerStream stream = demuxer.getStream(i);
      Decoder decoder = stream.getDecoder();
      if (decoder != null && decoder.getCodecType() == MediaDescriptor.Type.MEDIA_AUDIO) {
        audioStreamId = i;
        audioDecoder = decoder;
        
        break;
      }
    }
    
    if (audioStreamId == -1) {
      throw new RuntimeException("Could not find audio stream in container: " + demuxer.getURL());
    }
    
    return new AudioStreamInfo(audioStreamId, audioDecoder);
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
