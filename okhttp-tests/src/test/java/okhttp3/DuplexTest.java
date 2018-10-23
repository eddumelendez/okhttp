/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import okhttp3.internal.RecordingHeadersListener;
import okhttp3.internal.http2.Header;
import okhttp3.mockwebserver.DuplexResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okio.BufferedSource;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import static okhttp3.TestUtil.defaultClient;
import static okhttp3.tls.internal.TlsUtil.localhost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class DuplexTest {
  @Rule public final TestRule timeout = new Timeout(30_000, TimeUnit.MILLISECONDS);
  @Rule public final MockWebServer server = new MockWebServer();

  private HandshakeCertificates handshakeCertificates = localhost();
  private OkHttpClient client = defaultClient();

  @Ignore("delete me when done")
  @Test public void duplexCall() throws Exception {
    server.enqueue(new MockResponse()
        .clearHeaders()
        .addHeader("h1", "v1")
        .addHeader("h2", "v2")
        .setBody(new DuplexResponseBody() {
          @Override public Header.Listener onRequest(
              RecordedRequest request,
              BufferedSource requestBodySource,
              HttpSink responseBodySink
          ) throws IOException {
            // TODO(benoit): can we interleave headers and have a deterministic test?
            System.out.println("onRequest"
                + "\n\tRequest: " + request
                + "\n\tRequestBodySource: " + requestBodySource
                + "\n\tResponseBodySink: " + responseBodySink);
            final ArrayDeque<Headers> headersDeque = new ArrayDeque<>();
            //assertEquals("abc", requestBodySource.readUtf8Line());
            responseBodySink.headers(Headers.of("zyx", "wvut"));
            responseBodySink.sink().writeUtf8("rb1");
            responseBodySink.sink().writeUtf8("rb2");
            responseBodySink.sink().close();
            //assertEquals("klm", requestBodySource.readUtf8Line());
            //assertTrue(requestBodySource.exhausted());
            //assertEquals(Headers.of("def", "ghi"), headersDeque.poll());
            //assertEquals(Headers.of("nop", "qrst"), headersDeque.poll());
            return new Header.Listener() {
              @Override public void onHeaders(Headers headers) {
                headersDeque.add(headers);
              }
            };
          }
        }));
    enableProtocol(Protocol.HTTP_2);

    Call call = client.newCall(new Request.Builder()
        .url(server.url("/"))
        .duplex("POST")
        .build());

    Response response = call.execute();

    final ArrayDeque<Headers> responseHeadersDeque = new ArrayDeque<>();
    response.headersListener(new Header.Listener() {
      @Override public void onHeaders(Headers headers) {
        responseHeadersDeque.add(headers);
      }
    });

    HttpSink httpSink = response.httpSink();

    httpSink.sink().writeUtf8("abc\n");
    httpSink.sink().flush();
    //httpSink.headers(Headers.of("def", "ghi"));
    httpSink.sink().writeUtf8("klm\n");
    //httpSink.headers(Headers.of("nop", "qrst"));

    assertEquals("rb1rb2", response.body().string());
    assertEquals(new ArrayList<>(responseHeadersDeque),
        Arrays.asList(Headers.of("h1", "h2", "h2", "v2"), Headers.of("wyx", "wvut")));
  }

  @Test public void clientReadsHeadersDataHeadersData() throws IOException {
    server.enqueue(new MockResponse()
        .clearHeaders()
        .addHeader("h1", "v1")
        .addHeader("h2", "v2")
        .setBody(new DuplexResponseBody() {
          @Override public Header.Listener onRequest(RecordedRequest request,
              BufferedSource requestBodySource, HttpSink responseBodySink) throws IOException {
            // TODO(benoit): can we interleave headers and have a deterministic test?
            responseBodySink.sink().writeUtf8("staten");
            responseBodySink.sink().flush();
            responseBodySink.headers(Headers.of("brooklyn", "zoo"));
            responseBodySink.sink().writeUtf8(" island");
            responseBodySink.sink().close();
            return new Header.Listener() {
              @Override public void onHeaders(Headers headers) {
                fail();
              }
            };
          }
        }));
    enableProtocol(Protocol.HTTP_2);

    Call call = client.newCall(new Request.Builder()
        .url(server.url("/"))
        .duplex("POST")
        .build());

    Response response = call.execute();

    RecordingHeadersListener headersListener = new RecordingHeadersListener();
    response.headersListener(headersListener);

    assertEquals("staten island", response.body().source().readUtf8());
    assertEquals(Arrays.asList(Headers.of("h1", "h2", "h2", "v2"), Headers.of("brooklyn", "zoo")),
        headersListener.takeAll());
  }

  // TODO(oldergod) write tests for headers discared with 100 Continue

  /**
   * Tests that use this will fail unless boot classpath is set. Ex. {@code
   * -Xbootclasspath/p:/tmp/alpn-boot-8.0.0.v20140317}
   */
  private void enableProtocol(Protocol protocol) {
    enableTls();
    client = client.newBuilder()
        .protocols(Arrays.asList(protocol, Protocol.HTTP_1_1))
        .build();
    server.setProtocols(client.protocols());
  }

  private void enableTls() {
    client = client.newBuilder()
        .sslSocketFactory(
            handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager())
        .hostnameVerifier(new RecordingHostnameVerifier())
        .build();
    server.useHttps(handshakeCertificates.sslSocketFactory(), false);
  }
}
