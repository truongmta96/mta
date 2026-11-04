# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
# Optional CA for managed cloud proxy; normal builds use the JVM default store.
RUN --mount=type=secret,id=proxy_ca --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    if [ -f /run/secrets/proxy_ca ]; then keytool -importcert -noprompt -alias session-proxy -file /run/secrets/proxy_ca -keystore /tmp/build-truststore -storepass changeit; cp "$JAVA_HOME/lib/security/cacerts" /tmp/base-cacerts; keytool -importkeystore -noprompt -srckeystore /tmp/base-cacerts -srcstorepass changeit -destkeystore /tmp/build-truststore -deststorepass changeit; export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/build-truststore -Djavax.net.ssl.trustStorePassword=changeit"; fi; \
    mvn -B package && rm -f /tmp/build-truststore /tmp/base-cacerts
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/shop-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
