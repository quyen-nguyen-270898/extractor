package org.schabi.newpipe.examples;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

public final class SimpleDownloader extends Downloader {
  private final OkHttpClient client;

  public SimpleDownloader() {
    this.client = new OkHttpClient.Builder().build();
  }

  @Override
  public Response execute(final Request request)
      throws IOException, ReCaptchaException {
    final RequestBody body;
    if (request.dataToSend() != null) {
      body = RequestBody.create(request.dataToSend());
    } else {
      body = null;
    }

    final okhttp3.Request.Builder rb =
        new okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent",
                    "Mozilla/5.0 (compatible; SimpleDownloader/1.0)");

    // apply headers
    for (final Map.Entry<String, List<String>> h :
         request.headers().entrySet()) {
      final String name = h.getKey();
      final List<String> vals = h.getValue();
      if (vals != null) {
        for (final String v : vals) {
          rb.addHeader(name, v);
        }
      }
    }

    try (okhttp3.Response resp = client.newCall(rb.build()).execute()) {
      if (resp.code() == 429) {
        throw new ReCaptchaException("reCaptcha Challenge requested",
                                     request.url());
      }

      String bodyStr = null;
      if (resp.body() != null) {
        bodyStr = resp.body().string();
      }

      final Response r =
          new Response(resp.code(), resp.message(), resp.headers().toMultimap(),
                       bodyStr, resp.request().url().toString());
      return r;
    }
  }
}
