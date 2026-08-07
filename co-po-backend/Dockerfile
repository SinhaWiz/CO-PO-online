# Render has no native Java/Maven runtime - Docker is required. Multi-stage so the
# image that actually ships is just a JRE plus the built jar, not the full JDK, Maven
# wrapper, and source tree that were only needed to produce it.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/co-po-backend-*.jar app.jar

# Render sets PORT itself (see application.yml's server.port) - nothing to hardcode
# here beyond documenting that the app listens on HTTP.
ENTRYPOINT ["java", "-jar", "app.jar"]
