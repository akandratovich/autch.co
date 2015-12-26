package co.autch.codec;

import io.humble.video.AudioChannel;
import io.humble.video.AudioFormat;
import io.humble.video.Decoder;
import io.humble.video.Encoder;
import io.humble.video.MediaAudio;
import io.humble.video.MediaAudioResampler;

public class StreamResampler implements AutoCloseable {
  private final MediaAudioResampler resampler;
  
  private final MediaAudio          output;
  private final MediaAudio          input;
  
  public StreamResampler(Decoder decoder, Encoder encoder) {
    this.input = MediaAudio.make(decoder.getFrameSize(), decoder.getSampleRate(), decoder.getChannels(), decoder.getChannelLayout(), decoder.getSampleFormat());
    
    this.output = MediaAudio.make(encoder.getFrameSize(), encoder.getSampleRate(), encoder.getChannels(), encoder.getChannelLayout(), encoder.getSampleFormat());
    
    int inputSampleRate = input.getSampleRate();
    AudioFormat.Type inputMediaFormat = input.getFormat();
    AudioChannel.Layout inputLayout = input.getChannelLayout();
    
    int outputSampleRate = output.getSampleRate();
    AudioFormat.Type outputFormat = output.getFormat();
    AudioChannel.Layout outputLayout = output.getChannelLayout();
    
    this.resampler = MediaAudioResampler.make(outputLayout, outputSampleRate, outputFormat, inputLayout, inputSampleRate, inputMediaFormat);
    this.resampler.open();
  }
  
  public MediaAudio getOutput() {
    return output;
  }
  
  public MediaAudio getInput() {
    return input;
  }
  
  public int resample() {
    return resampler.resample(output, input);
  }
  
  @Override
  public void close() {
    input.delete();
    output.delete();
  }
}
