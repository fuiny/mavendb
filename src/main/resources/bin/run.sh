#!/bin/bash
#
# Script to trigger sync the latest central maven index to local box
#

# Function
timestamp() {
  retval=$(date '+%Y-%m-%d %T.%3N')
  echo $(basename $0) $retval
}

if [ $# -eq 0 ]; then
  echo "$0 $(timestamp) No arguments supplied. The repos name is expected."
  exit 1
fi


# If do not want to use the java command in the path, we can change to a different Java command
JAVA_CMD=java

# Get current directory
BASEDIR="$( cd "$(dirname "$0")" ; pwd -P )"
echo "$(timestamp) Base Directory is $BASEDIR"
echo ""

configfile="../etc/repos-$1.properties"
if [ ! -f "$BASEDIR/$configfile" ]; then
  echo "$(timestamp) Configuration file does NOT exist: $configfile"
  echo "$(timestamp) Please provide the correct configuration file name"
  exit 1
else
  echo "$(timestamp) Configuration file exists: $configfile"
  echo ""
fi


echo "$(timestamp) Read DB Configuration data"
dbhost=$(grep "jakarta.persistence.jdbc.url"       $BASEDIR/../etc/config.properties | cut -d'/' -f3 | cut -d':' -f1)
dbport=$(grep "jakarta.persistence.jdbc.url"       $BASEDIR/../etc/config.properties | cut -d'/' -f3 | cut -d':' -f2)
dbuser=$(grep "jakarta.persistence.jdbc.user"      $BASEDIR/../etc/config.properties | cut -d'=' -f2)
dbpass=$(grep "jakarta.persistence.jdbc.password"  $BASEDIR/../etc/config.properties | cut -d'=' -f2)
echo "$(timestamp) Database Host: $dbhost"
echo "$(timestamp) Database Port: $dbport"
echo "$(timestamp) Database User: $dbuser"
echo "$(timestamp) Database Pass: ${dbpass//?/*}"

echo "$(timestamp) Create schema"
mysql -h $dbhost -P $dbport -u $dbuser -p$dbpass  <  $BASEDIR/../db/create.sql

echo "$(timestamp) Create schema finished"
echo ""

# Set JAVA_OPTS

JAVA_OPTS=
JAVA_OPTS="${JAVA_OPTS} \
 -showversion \
 -verbose:gc \
 -verbose:module \
 -Xdiag \
 -Xlog:codecache,gc*,safepoint:file=../log/jvmunified.log:level,tags,time,uptime,pid:filesize=209715200,filecount=10 \
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
"

JAVA_OPTS="${JAVA_OPTS} \
 -XX:+DebugNonSafepoints \
 -XX:FlightRecorderOptions=repository=../log \
 -XX:StartFlightRecording=disk=true,dumponexit=true,filename=../log/profile.jfr,name=Profiling,settings=profile \
"

RUN_CMD="$JAVA_CMD $JAVA_OPTS -Xmx16g -server -jar $BASEDIR/../mavendb.jar -r $1"
echo "$(timestamp) $RUN_CMD"
eval               $RUN_CMD

echo "$(timestamp) Running data-refresh.sql"
date
mysql -h $dbhost -P $dbport -u $dbuser -p$dbpass -N < "$BASEDIR/../db/data-refresh.sql"
date

echo "Finished"
