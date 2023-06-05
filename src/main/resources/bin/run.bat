
@echo off

rem # If do not want to use the java command in the path, we can change to a different Java command
set JAVA_CMD=java

set  JAVA_OPTS=
set "JAVA_OPTS=%JAVA_OPTS% -showversion "
set "JAVA_OPTS=%JAVA_OPTS% -verbose:gc "
set "JAVA_OPTS=%JAVA_OPTS% -verbose:module "
set "JAVA_OPTS=%JAVA_OPTS% -Xdiag "
set "JAVA_OPTS=%JAVA_OPTS% -Xlog:codecache,gc*,safepoint:file=../log/jvmunified.log:level,tags,time,uptime,pid:filesize=209715200,filecount=10 "
set "JAVA_OPTS=%JAVA_OPTS% -XshowSettings:all "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+UnlockDiagnosticVMOptions "
set "JAVA_OPTS=%JAVA_OPTS% -XX:NativeMemoryTracking=summary "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+ExtensiveErrorReports "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+HeapDumpOnOutOfMemoryError "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+PerfDataSaveToFile "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+PrintClassHistogram "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+PrintCommandLineFlags "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+PrintConcurrentLocks "
set "JAVA_OPTS=%JAVA_OPTS% -XX:+PrintNMTStatistics "

set "JAVA_OPTS=%JAVA_OPTS% -XX:+DebugNonSafepoints "
set "JAVA_OPTS=%JAVA_OPTS% -XX:FlightRecorderOptions=repository=../log "
set "JAVA_OPTS=%JAVA_OPTS% -XX:StartFlightRecording=duration=10m,disk=true,dumponexit=true,filename=../log/profile.jfr,name=Profiling,settings=profile "


set    RUN_CMD=%JAVA_CMD% %JAVA_OPTS% -Xmx16g -server -jar ../maven-repos-db.jar central
echo  %RUN_CMD%
%RUN_CMD%

rem # echo -n "Running data-refresh.sql"
rem # mysql --login-path=binarydocjvmadm < "$BASEDIR/data-refresh.sql"

echo Finished
