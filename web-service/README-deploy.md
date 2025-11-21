Render Deployment & Local Build

Quick summary
- Build locally with Docker (recommended) or let CI build the image.
- Deploy to Render using the included `render.yaml` or by pointing Render to `examples/web-service/Dockerfile.multistage`.

1) Build locally with Docker (if you have a working Docker daemon):

```bash
# build image
docker build -f examples/web-service/Dockerfile.multistage -t yourdockerid/newpipe-web-service:latest .

# run for smoke test (expose port 7000)
docker run --rm -p 7000:7000 \
  -e PROXY_API_KEY=changeme \
  -e YT_API_KEY=your_youtube_api_key_optional \
  yourdockerid/newpipe-web-service:latest

# then test
curl http://127.0.0.1:7000/health
curl -sS 'http://127.0.0.1:7000/api/stream?query=https://www.youtube.com/watch?v=CTgCc4rmxA0'
```

2) Use GitHub Actions to build & push the image (workflow at `.github/workflows/docker-build.yml`).
   - Set `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` in repository Secrets.
   - Push to `main` to trigger the workflow.

3) Deploy to Render
   - Option A (Render builds from repo): create a new Web Service on Render and use the `Docker` environment. Set the Dockerfile path to `examples/web-service/Dockerfile.multistage`.
   - Option B (Render deploys an image): push the image built by CI to Docker Hub and configure Render to use that image.
   - Set the health check path to `/health` in the Render service settings.
   - Add environment variables in Render: `PROXY_API_KEY` (required if you plan to use `/api/proxy`) and optionally `YT_API_KEY` for more reliable name-based search.

Helpful Render configuration values
- `Start Command`: `java -jar /app/web-service.jar`
- `Health Check Path`: `/health`
- `Environment`: `Docker`

Notes & troubleshooting
- If the example fails to build inside Render due to file-mode or Gradle toolchain errors, build the image via CI (GitHub Actions) and deploy the pushed image instead.
- The service returns JSON from `/api/stream?query=` with `audioUrl` and `mime` fields.
- Use `/api/proxy?url=...` when clients cannot use signed/short-lived direct URLs. Protect the proxy with `PROXY_API_KEY`.
- NewPipeExtractor is GPLv3; public deployment may require you to make source available. Confirm licensing obligations before deploying publicly.
