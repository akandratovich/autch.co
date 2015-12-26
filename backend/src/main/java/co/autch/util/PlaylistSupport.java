package co.autch.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import co.autch.http.WebClient;

import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.iheartradio.m3u8.Encoding;
import com.iheartradio.m3u8.PatchedM3uParser;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.TrackData;

public final class PlaylistSupport {
  private final PatchedM3uParser m3u8 = new PatchedM3uParser(Encoding.UTF_8);
  
  public String stream(WebClient client, String channel) throws Exception {
    Playlist playlist = streamPlaylist(client, channel);
    MasterPlaylist master = playlist.getMasterPlaylist();
    
    PlaylistData lowBitrate = null;
    for (PlaylistData data : master.getPlaylists()) {
      if (lowBitrate == null) {
        lowBitrate = data;
      }
      
      if (data.getStreamInfo().getBandwidth() < lowBitrate.getStreamInfo().getBandwidth()) {
        lowBitrate = data;
      }
    }
    
    return lowBitrate.getLocation();
  }
  
  public Collection<String> track(WebClient client, String location) throws Exception {
    Collection<String> coll = Lists.newArrayList();
    
    Playlist playlist = trackPlaylist(client, location);
    for (TrackData data : playlist.getMediaPlaylist().getTracks()) {
      String path = data.getLocation();
      coll.add(path);
    }
    
    return coll;
  }
  
  private Playlist trackPlaylist(WebClient client, String location) throws Exception {
    byte[] data = client.getContent(location);
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
      return m3u8.parse(bais);
    }
  }
  
  private Playlist streamPlaylist(WebClient client, String channel) throws Exception {
    String url = String.format(USHER_API, channel);
    
    TokenInfo ti = token(client, channel);
    Map<String, String> query = userQuery(ti);
    
    byte[] data = client.getContent(url, query);
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
      return m3u8.parse(bais);
    }
  }
  
  private TokenInfo token(WebClient client, String channel) throws IOException {
    String url = String.format(TOKEN_API, channel);
    byte[] content = client.getContent(url);
    
    Map<String, String> object = Json.read(content, mapType);
    
    String token = object.get("token");
    String signature = object.get("sig");
    
    return new TokenInfo(token, signature);
  }
  
  private Map<String, String> userQuery(TokenInfo info) {
    return ImmutableMap.<String, String> builder()
        .put("player", "twitchweb")
        .put("allow_audio_only", "true")
        .put("allow_source", "true")
        .put("type", "any")
        .put("p", "1")
        .put("token", info.token())
        .put("sig", info.signature())
        .build();
  }
  
  private static class TokenInfo {
    private final String token;
    private final String signature;
    
    public TokenInfo(String token, String signature) {
      this.token = token;
      this.signature = signature;
    }
    
    public String signature() {
      return signature;
    }
    
    public String token() {
      return token;
    }
  }
  
  private static final MapType mapType   = MapType.construct(Map.class, SimpleType.construct(String.class), SimpleType.construct(String.class));
  
  private static final String  USHER_API = "http://usher.twitch.tv/api/channel/hls/%s.m3u8";
  private static final String  TOKEN_API = "http://api.twitch.tv/api/channels/%s/access_token";
}
