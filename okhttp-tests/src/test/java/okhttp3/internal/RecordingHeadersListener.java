package okhttp3.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Headers;
import okhttp3.internal.http2.Header;

public final class RecordingHeadersListener implements Header.Listener {
  final ArrayDeque<Headers> receivedHeaders = new ArrayDeque<>();

  @Override public void onHeaders(Headers headers) {
    receivedHeaders.add(headers);
  }

  public List<Headers> takeAll() {
    List<Headers> result = new ArrayList<>();
    for (Headers headers; (headers = receivedHeaders.poll()) != null; ) {
      result.add(headers);
    }
    return result;
  }
}
