Render Deployment & Local Build

1) Build locally with Docker (if you have a working Docker daemon):

```bash
# build image
docker build -f examples/web-service/Dockerfile.multistage -t yourdockerid/newpipe-web-service:latest .

# run for smoke test
docker run --rm -p 7000:7000 yourdockerid/newpipe-web-service:latest

# then test
curl http://127.0.0.1:7000/health
curl -sS 'http://127.0.0.1:7000/api/stream?query=https://www.youtube.com/watch?v=CTgCc4rmxA0'
```

2) Use GitHub Actions to build & push the image (we included a workflow in `.github/workflows/docker-build.yml`).
   - Set `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` in repository Secrets.
   - Push to `main` to trigger the workflow.

3) Deploy to Render
   - Create a new Web Service on Render.
   - Choose Docker and point Render to `examples/web-service/Dockerfile.multistage` (or provide your pushed image).
   - Set the health check path to `/health`.
   - Ensure environment variable `PORT` is respected (the app uses Render's `PORT`).

Notes
- The service returns JSON from `/api/stream?query=` with `audioUrl` and `mime` fields.
- If clients (ESP32) cannot fetch short-lived signed URLs directly, use `/api/proxy?url=...` to stream via this service.
- NewPipeExtractor is GPLv3; deploying publicly may have license obligations.
