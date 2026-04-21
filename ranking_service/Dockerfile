FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /code

COPY pom.xml /code/

RUN --mount=type=cache,target=/root/.m2,id=m2-ranking mvn dependency:go-offline -B

COPY src /code/src

RUN --mount=type=cache,target=/root/.m2,id=m2-ranking mvn package -DskipTests -Dspotless.skip=true -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /deployments

COPY --from=build --chown=185 /code/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /code/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /code/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /code/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]