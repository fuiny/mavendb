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


BASEDIR="$( cd "$(dirname "$0")" ; pwd -P )"

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

RUN_CMD="$JAVA_CMD $JAVA_OPTS -Xmx16g -server -jar $BASEDIR/../binarydoc-mvnrepos.jar -r $1"
echo "$RUN_CMD"
eval  $RUN_CMD

date
echo "Running data-refresh.sql"
mysql --login-path=binarydocjvmadm -N < "$BASEDIR/data-refresh.sql"
date

echo "Finished"
