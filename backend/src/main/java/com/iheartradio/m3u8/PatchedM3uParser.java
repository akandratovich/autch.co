package com.iheartradio.m3u8;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.iheartradio.m3u8.data.Playlist;

public class PatchedM3uParser {
  private final Encoding                    mEncoding;
  private final Map<String, IExtTagHandler> mExtTagHandlers = new HashMap<String, IExtTagHandler>();
  
  public PatchedM3uParser(Encoding encoding) {
    mEncoding = encoding;
    
    putHandlers(
        ExtTagHandler.EXTM3U_HANDLER,
        ExtTagHandler.EXT_X_VERSION_HANDLER,
        MasterPlaylistTagHandler.EXT_X_MEDIA,
        MasterPlaylistTagHandler.EXT_X_STREAM_INF,
        MediaPlaylistTagHandler.EXT_X_TARGETDURATION,
        MediaPlaylistTagHandler.EXT_X_MEDIA_SEQUENCE,
        MediaPlaylistTagHandler.EXT_X_ALLOW_CACHE,
        MediaPlaylistTagHandler.EXTINF,
        MediaPlaylistTagHandler.EXT_X_KEY);
  }
  
  public Playlist parse(InputStream inputStream) throws ParseException {
    final ParseState state = new ParseState(mEncoding);
    final LineHandler playlistHandler = new PlaylistHandler();
    final LineHandler trackHandler = new TrackHandler();
    
    try (ExtendedM3uScanner mScanner = new ExtendedM3uScanner(inputStream, mEncoding)) {
      while (mScanner.hasNext()) {
        final String line = mScanner.next();
        checkWhitespace(line);
        
        if (line.length() == 0 || isComment(line)) {
          continue;
        } else {
          if (isExtTag(line)) {
            final String tagKey = getExtTagKey(line);
            final IExtTagHandler handler = mExtTagHandlers.get(tagKey);
            
            if (handler != null) {
              handler.handle(line, state);
            }
          } else if (state.isMaster()) {
            playlistHandler.handle(line, state);
          } else if (state.isMedia()) {
            trackHandler.handle(line, state);
          } else {
            throw ParseException.create(ParseExceptionType.UNKNOWN_PLAYLIST_TYPE, line);
          }
        }
      }
      
      return state.buildPlaylist();
    }
  }
  
  private void putHandlers(IExtTagHandler... handlers) {
    if (handlers != null) {
      for (IExtTagHandler handler : handlers) {
        mExtTagHandlers.put(handler.getTag(), handler);
      }
    }
  }
  
  private void checkWhitespace(final String line) throws ParseException {
    if (!isComment(line)) {
      if (line.length() != line.trim().length()) {
        throw ParseException.create(ParseExceptionType.WHITESPACE_IN_TRACK, line);
      }
    }
  }
  
  private boolean isComment(final String line) {
    return line.startsWith(Constants.COMMENT_PREFIX) && !isExtTag(line);
  }
  
  private boolean isExtTag(final String line) {
    return line.startsWith(Constants.EXT_TAG_PREFIX);
  }
  
  private String getExtTagKey(final String line) {
    int index = line.indexOf(Constants.EXT_TAG_END);
    
    if (index == -1) {
      return line.substring(1);
    } else {
      return line.substring(1, index);
    }
  }
}
