FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /code

COPY pom.xml /code/

RUN --mount=type=cache,target=/root/.m2,id=m2-matchmaking mvn dependency:go-offline -B

COPY src /code/src

RUN --mount=type=cache,target=/root/.m2,id=m2-matchmaking mvn package -DskipTests -Dspotless.skip=true -B

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24
WORKDIR /deployments

COPY --from=build --chown=185 /code/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /code/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /code/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /code/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]