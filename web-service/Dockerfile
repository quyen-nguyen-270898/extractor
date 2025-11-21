FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/web-service.jar /app/web-service.jar
EXPOSE 7000
ENTRYPOINT ["java","-jar","/app/web-service.jar"]
