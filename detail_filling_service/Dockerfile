FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /code

# Use a dedicated Maven local repository inside the build context to avoid lock contention across parallel builds
ENV MAVEN_OPTS="-Dmaven.repo.local=/code/.m2/repository"

LABEL version="1.0"

COPY pom.xml /code/

# Pre‑download all dependencies into the isolated repo
RUN mvn dependency:go-offline -B

COPY src /code/src

# Build the fast‑jar using the isolated repo
RUN mvn -B package -DskipTests -Dspotless.skip=true -Dquarkus.package.type=fast-jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /deployments

COPY --from=build /code/target/quarkus-app/quarkus-run.jar /deployments/quarkus-run.jar
COPY --from=build /code/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build /code/target/quarkus-app/app/ /deployments/app/
COPY --from=build /code/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]