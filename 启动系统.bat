@echo off
chcp 65001 >nul
title Lab System Launcher
setlocal

rem ============================================================
rem  One-click launcher for the Lab Reservation & Equipment
rem  Maintenance System.  Double-click this file to start both
rem  backend (8080) and frontend (5173), then open the browser.
rem ============================================================

rem --- MySQL root password: 占位符，提交到仓库前请勿填写真实密码；本地运行时请改为你的密码 -------
set "MYSQL_PASSWORD=<你的MySQL密码>"

set "ROOT=%~dp0"

echo.
echo   ==========================================================
echo     Lab Reservation and Equipment Maintenance System
echo   ==========================================================
echo     Backend  : http://localhost:8080
echo     Frontend : http://localhost:5173
echo     Accounts : admin / t_wang / s2021001 / r_zhang
echo     Password : 123456
echo   ==========================================================
echo.

if not exist "%ROOT%lab-backend\target\lab-backend.jar" (
    echo   [ERROR] lab-backend.jar not found.
    echo   Run first:  cd lab-backend  ^&^&  mvn -DskipTests package
    echo.
    pause
    exit /b 1
)

echo   [1/2] Starting backend on port 8080 ...
start "lab-backend" cmd /k java -jar "%ROOT%lab-backend\target\lab-backend.jar" --server.port=8080

echo         Waiting 15 seconds for Spring Boot to boot ...
timeout /t 15 /nobreak >nul

echo   [2/2] Starting frontend on port 5173 ...
cd /d "%ROOT%lab-frontend"
start "lab-frontend" cmd /k npm run dev

echo         Waiting 8 seconds for Vite to be ready ...
timeout /t 8 /nobreak >nul

start "" http://localhost:5173

echo.
echo   Done. Browser should be open now.
echo   To stop the system, just close the two black windows.
echo.
pause
