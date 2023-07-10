# Build stage
FROM maven:3-eclipse-temurin-17 AS build
COPY .git    /app/.git
COPY pom.xml /app/
COPY src     /app/src
RUN  apt install git
RUN  cd /app && mvn clean package

# Run stage
FROM eclipse-temurin:17-alpine

ENV MAVENDB_MYSQL_HOST=localhost
ENV MAVENDB_MYSQL_PORT=3306
ENV MAVENDB_MYSQL_USER=fuinyadmin
ENV MAVENDB_MYSQL_PASS=123456

RUN mkdir -p /opt/fuiny/mavendb
COPY --from=build /app/target/mavendb.jar /opt/fuiny/mavendb
CMD ["/opt/fuiny/mavendb/bin/run.sh"]
