@echo off
setlocal
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%\gradle\wrapper\*
java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
