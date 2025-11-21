Minimal example web service using NewPipeExtractor

This example shows a minimal HTTP service (Javalin) that exposes `/api/stream?query=` and
returns the first audio stream URL from a StreamExtractor.

Notes:
- This is intentionally minimal. A proper implementation should implement search and better
  error handling, caching and respect licensing.
- The example currently expects direct stream URLs or service-specific URLs (e.g. a YouTube
  watch URL) in the `query` parameter. Implementing search requires using `SearchExtractor`
  classes from the library.

Build & Run locally

```bash
# from repository root
cd examples/web-service
./gradlew shadowJar
java -jar build/libs/web-service.jar
```

Docker (for Render)

```bash
# from examples/web-service
./gradlew shadowJar
# build image
docker build -t web-service:latest .
# run
docker run -p 7000:7000 web-service:latest
```

Deploy on Render

- Create a new "Web Service" on Render.
- Use your repo and set the build command to `./gradlew :examples:web-service:shadowJar` and the
  start command to `java -jar examples/web-service/build/libs/web-service.jar`.
- Set the port to `7000` (Render will set `PORT` env var — you can adapt the app to read it).
- Add any necessary environment variables.
