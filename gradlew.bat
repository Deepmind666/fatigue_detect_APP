@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

endlocal

