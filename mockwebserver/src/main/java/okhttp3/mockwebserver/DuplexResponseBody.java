package okhttp3.mockwebserver;

import java.io.IOException;
import okhttp3.HttpSink;
import okhttp3.internal.http2.Header;
import okio.BufferedSource;

public interface DuplexResponseBody {
  Header.Listener onRequest(
      RecordedRequest request,
      BufferedSource requestBodySource,
      HttpSink responseBodySink) throws IOException;
}
