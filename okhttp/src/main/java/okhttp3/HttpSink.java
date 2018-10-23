package okhttp3;

import java.io.Closeable;
import java.io.IOException;
import okio.BufferedSink;

public interface HttpSink extends Closeable {
  BufferedSink sink();
  void headers(Headers headers) throws IOException;
}

