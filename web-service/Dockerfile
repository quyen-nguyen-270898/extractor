# Multi-stage Dockerfile: build then run
FROM eclipse-temurin:17 as build
WORKDIR /workspace
COPY . .
# Use Gradle wrapper to build the example jar only. Set example.buildDir to /tmp to
# avoid permission issues on filesystems that don't support POSIX modes.
RUN ./gradlew -Dexample.buildDir=/tmp/example-build :examples/web-service:shadowJar -x :extractor:extractIncludeProto --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy only the built jar
COPY --from=build /workspace/examples/web-service/build/libs/*.jar /app/web-service.jar
EXPOSE 7000
ENV PORT=7000
ENTRYPOINT ["java","-jar","/app/web-service.jar"]
