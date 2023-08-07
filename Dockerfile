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
ENV MAVENDB_ARGS="-r central"

RUN mkdir -p                               /opt/fuiny/mavendb
RUN mkdir -p                               /opt/fuiny/mavendb/db
RUN mkdir -p                               /opt/fuiny/mavendb/etc
RUN mkdir -p                               /opt/fuiny/mavendb/lib
RUN mkdir -p                               /opt/fuiny/mavendb/log

COPY --from=build /app/target/mavendb.jar  /opt/fuiny/mavendb
COPY --from=build /app/target/db/          /opt/fuiny/mavendb/db
COPY --from=build /app/target/etc/         /opt/fuiny/mavendb/etc
COPY --from=build /app/target/lib/         /opt/fuiny/mavendb/lib

ENTRYPOINT java \
 -showversion \
 -verbose:gc \
 -verbose:module \
 -Xdiag \
 -Xlog:codecache,gc*,safepoint:file=/opt/fuiny/mavendb/log/jvmunified.log:level,tags,time,uptime,pid:filesize=209715200,filecount=10 \
 -XshowSettings:all \
 -XX:+UnlockDiagnosticVMOptions \
 -XX:NativeMemoryTracking=summary \
 -XX:+ExtensiveErrorReports \
 -XX:+HeapDumpOnOutOfMemoryError \
 -XX:+PerfDataSaveToFile \
 -XX:+PrintClassHistogram \
 -XX:+PrintCommandLineFlags \
 -XX:+PrintConcurrentLocks \
 -XX:+PrintNMTStatistics \
 -XX:+DebugNonSafepoints \
 -XX:FlightRecorderOptions=repository=/opt/fuiny/mavendb/log \
 -XX:StartFlightRecording=disk=true,dumponexit=true,filename=/opt/fuiny/mavendb/log/profile.jfr,name=Profiling,settings=profile \
 -XX:MaxRAMPercentage=85 -server -jar /opt/fuiny/mavendb/mavendb.jar ${MAVENDB_ARGS}

