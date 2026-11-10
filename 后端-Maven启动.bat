@echo off
chcp 65001 >nul
title Lab Backend - Maven Run
setlocal

rem ============================================================
rem  Run backend with Maven (spring-boot:run).
rem
rem  Why this script exists:
rem  spring-boot-maven-plugin 3.3.5 always passes the classpath to
rem  the forked JVM through a temporary .argfile.  When the project
rem  path contains non-ASCII characters (Chinese), the launcher
rem  mis-decodes that file and fails with
rem      ClassNotFoundException: com.lab.system.LabApplication
rem  Mapping the project to an ASCII drive letter (subst) avoids it.
rem ============================================================

rem --- MySQL root password: 占位符，提交到仓库前请勿填写真实密码；本地运行时请改为你的密码 -------
set "MYSQL_PASSWORD=<你的MySQL密码>"

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

echo.
echo   Project : %ROOT%
echo   Mapping : X:  -^>  above
echo.

subst X: "%ROOT%" >nul 2>&1
if not exist "X:\lab-backend\pom.xml" (
    echo   [ERROR] subst failed - drive X: may already be in use.
    echo   Check with:  subst
    echo.
    pause
    exit /b 1
)

cd /d "X:\lab-backend"

echo   Running: mvn spring-boot:run
echo   Backend will be on http://localhost:8080
echo   Press Ctrl+C to stop.
echo.

call mvn spring-boot:run

echo.
echo   Backend stopped. Removing drive mapping X: ...
subst X: /d >nul 2>&1
endlocal
pause
