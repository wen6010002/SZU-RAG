FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/szu-rag-backend-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
