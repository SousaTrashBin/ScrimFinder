FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /code

LABEL version="1.0"

COPY pom.xml /code/

RUN --mount=type=cache,target=/root/.m2,id=m2-detail mvn dependency:go-offline -B

COPY src /code/src

RUN --mount=type=cache,target=/root/.m2,id=m2-detail mvn package -DskipTests -Dspotless.skip=true -B
RUN cp target/deployment/*-runner.jar target/quarkus-run.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /deployments

COPY --from=build --chown=185 /code/target/quarkus-run.jar /deployments/quarkus-run.jar

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]
