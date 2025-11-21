package org.schabi.newpipe.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

public class WebServiceMain {
  private static final List<String> PREFERRED_MIMES =
      List.of("audio/mpeg", "audio/mp3", "audio/mp4", "audio/aac",
              "audio/x-m4a", "audio/webm");

  // Simple in-memory TTL cache for proxied URLs (key -> (url, expiry))
  private static final Map<String, CachedEntry> URL_CACHE = new HashMap<>();
  private static final long CACHE_TTL_SECONDS = 45; // cache signed URLs briefly

  // Naive per-IP rate limiter: allow N requests per window
  private static final Map<String, Deque<Instant>> IP_REQUESTS =
      new HashMap<>();
  private static final int RATE_LIMIT = 10;          // requests
  private static final int RATE_WINDOW_SECONDS = 60; // per 60s

  // API key header name
  private static final String API_KEY_HEADER = "X-API-KEY";

  public static void main(String[] args) throws Exception {
    // Init downloader (using simple OkHttp-based downloader)
    final SimpleDownloader sd = new SimpleDownloader();
    NewPipe.init(sd);

    // read PORT from env (Render provides PORT), default to 7000
    int port = 7000;
    final String portEnv = System.getenv("PORT");
    if (portEnv != null) {
      try {
        port = Integer.parseInt(portEnv);
      } catch (final NumberFormatException ignored) {
      }
    }

    Javalin app = Javalin.create().start(port);
    // set default content type for responses
    app.before(ctx -> ctx.contentType("application/json"));
    app.get("/health", ctx -> ctx.result("ok"));

    app.get("/api/stream", WebServiceMain::handleStreamByQuery);
    // simple proxy endpoint to stream the audio via this service
    app.get("/api/proxy", WebServiceMain::handleProxy);
  }

  private static void handleStreamByQuery(Context ctx) {
    final String query = ctx.queryParam("query");
    if (query == null || query.isEmpty()) {
      ctx.status(400).result("{\"error\":\"missing query\"}");
      return;
    }

    try {
      // Decide whether query is a URL or a search string
      StreamingService yt = NewPipe.getService("YouTube");
      final boolean isUrl = query.matches("(?i)https?://.*");

      StreamExtractor se;
      String streamUrl = null;

      if (isUrl) {
        streamUrl = query;
      } else {
        // If YT_API_KEY is provided, prefer YouTube Data API search (more
        // reliable)
        final String ytApiKey = System.getenv("YT_API_KEY");
        if (ytApiKey != null && !ytApiKey.isBlank()) {
          try {
            final String qenc =
                URLEncoder.encode(query, StandardCharsets.UTF_8);
            final String apiUrl =
                "https://www.googleapis.com/youtube/v3/search?part=id&type=video&maxResults=1&q=" +
                qenc + "&key=" + ytApiKey;
            HttpURLConnection conn =
                (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestProperty("User-Agent",
                                    "NewPipe-Extractor-Example/1.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
              ObjectMapper om = new ObjectMapper();
              JsonNode root = om.readTree(conn.getInputStream());
              JsonNode items = root.path("items");
              if (items.isArray() && items.size() > 0) {
                String vid =
                    items.get(0).path("id").path("videoId").asText(null);
                if (vid != null && !vid.isEmpty()) {
                  streamUrl = "https://www.youtube.com/watch?v=" + vid;
                }
              }
            }
          } catch (Exception ignored) {
          }
        }
        // fallback to extractor search when no API key or API didn't return a
        // result
        if (streamUrl == null) {
          final var searchExtractor = yt.getSearchExtractor(query);
          searchExtractor.fetchPage();
          final InfoItemsPage<InfoItem> page = searchExtractor.getInitialPage();
          final var items = page.getItems();
          if (items == null || items.isEmpty()) {
            ctx.status(404).result("{\"error\":\"no search results\"}");
            return;
          }
          // Take first InfoItem's url
          streamUrl = items.get(0).getUrl();
        }
      }

      se = NewPipe.getService("YouTube").getStreamExtractor(streamUrl);
      se.fetchPage();
      List<AudioStream> audios = se.getAudioStreams();
      if (audios == null || audios.isEmpty()) {
        ctx.status(404).result("{\"error\":\"no audio streams\"}");
        return;
      }

      AudioStream chosen = choosePreferred(audios, Set.copyOf(PREFERRED_MIMES));
      ctx.json(new StreamResponse(
          chosen.getContent(),
          chosen.getFormat() != null ? chosen.getFormat().getMimeType() : ""));
    } catch (ExtractionException | IOException e) {
      ctx.status(500).result("{\"error\":\"" + e.getMessage() + "\"}");
    }
  }

  private static AudioStream choosePreferred(List<AudioStream> audios,
                                             Set<String> preferred) {
    // 1) prefer streams whose mime matches preferred list
    for (AudioStream a : audios) {
      final var f = a.getFormat();
      if (f != null && preferred.contains(f.getMimeType())) {
        return a;
      }
    }
    // 2) then prefer any with a URL
    for (AudioStream a : audios) {
      if (a.isUrl())
        return a;
    }
    // 3) fallback to first
    return audios.get(0);
  }

  // Proxy: fetch the given remote URL and stream it back to the client
  private static void handleProxy(Context ctx) throws IOException {
    final String url = ctx.queryParam("url");
    if (url == null || url.isEmpty()) {
      ctx.status(400).result("{\"error\":\"missing url\"}");
      return;
    }

    // API key enforcement (set PROXY_API_KEY env var on the server)
    final String expected = System.getenv("PROXY_API_KEY");
    if (expected != null && !expected.isEmpty()) {
      final String provided = ctx.header(API_KEY_HEADER);
      if (provided == null || !provided.equals(expected)) {
        ctx.status(401).result("{\"error\":\"missing or invalid API key\"}");
        return;
      }
    }

    // rate limit by IP
    final String ip = ctx.ip();
    final Instant now = Instant.now();
    synchronized (IP_REQUESTS) {
      Deque<Instant> dq =
          IP_REQUESTS.computeIfAbsent(ip, k -> new LinkedList<>());
      // purge old
      while (!dq.isEmpty() &&
             dq.peekFirst().isBefore(now.minusSeconds(RATE_WINDOW_SECONDS))) {
        dq.removeFirst();
      }
      if (dq.size() >= RATE_LIMIT) {
        ctx.status(429).result("{\"error\":\"rate limit exceeded\"}");
        return;
      }
      dq.addLast(now);
    }

    // Basic URL validation
    if (!url.matches("(?i)https?://.*")) {
      ctx.status(400).result("{\"error\":\"invalid url\"}");
      return;
    }

    // Check cache
    final long nowEpoch = Instant.now().getEpochSecond();
    synchronized (URL_CACHE) {
      CachedEntry ce = URL_CACHE.get(url);
      if (ce != null && ce.expiryEpoch >= nowEpoch) {
        // return cached direct mapping by proxying directly to cachedUrl
        streamUrlThrough(ctx, ce.cachedUrl);
        return;
      }
    }

    // Not cached: open connection to upstream and stream
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestProperty("User-Agent", "NewPipe-Extractor-Example/1.0");
    conn.setConnectTimeout(10_000);
    conn.setReadTimeout(30_000);
    conn.connect();

    int code = conn.getResponseCode();
    if (code >= 400) {
      ctx.status(502).result("{\"error\":\"upstream returned " + code + "\"}");
      return;
    }

    final String contentType = conn.getContentType();
    if (contentType != null)
      ctx.contentType(contentType);
    ctx.status(200);

    // Cache the proxied URL location
    synchronized (URL_CACHE) {
      URL_CACHE.put(url, new CachedEntry(url, nowEpoch + CACHE_TTL_SECONDS));
    }

    try (InputStream in = conn.getInputStream()) {
      // stream bytes directly
      ctx.result(in);
    }
  }

  private static void streamUrlThrough(Context ctx, String cachedUrl)
      throws IOException {
    HttpURLConnection conn =
        (HttpURLConnection) new URL(cachedUrl).openConnection();
    conn.setRequestProperty("User-Agent", "NewPipe-Extractor-Example/1.0");
    conn.setConnectTimeout(10_000);
    conn.setReadTimeout(30_000);
    conn.connect();

    int code = conn.getResponseCode();
    if (code >= 400) {
      ctx.status(502).result("{\"error\":\"upstream returned " + code + "\"}");
      return;
    }
    final String contentType = conn.getContentType();
    if (contentType != null)
      ctx.contentType(contentType);
    ctx.status(200);
    try (InputStream in = conn.getInputStream()) {
      ctx.result(in);
    }
  }

  public static class StreamResponse {
    public final String audioUrl;
    public final String mime;

    public StreamResponse(String audioUrl, String mime) {
      this.audioUrl = audioUrl;
      this.mime = mime;
    }
  }

  private static class CachedEntry {
    final String cachedUrl;
    final long expiryEpoch;

    CachedEntry(String cachedUrl, long expiryEpoch) {
      this.cachedUrl = cachedUrl;
      this.expiryEpoch = expiryEpoch;
    }
  }
}
