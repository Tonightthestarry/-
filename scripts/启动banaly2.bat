@echo off
chcp 65001 >nul
echo ============================================
echo   banaly2 一键启动（后端 + 前端）
echo ============================================
echo.

REM ---- 杀旧进程 ----
echo [1/3] 清理旧进程...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8088 ^| findstr LISTENING') do (
  echo    关闭 8088 进程 PID=%%a
  taskkill /F /PID %%a >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5173 ^| findstr LISTENING') do (
  echo    关闭 5173 进程 PID=%%a
  taskkill /F /PID %%a >nul 2>&1
)
timeout /t 2 >nul

REM ---- 启动后端 ----
echo.
echo [2/3] 启动后端 (端口 8088)...
cd /d D:\massdatanaly\banaly2-java
start "banaly2-backend" /MIN cmd /c "mvn spring-boot:run > D:\massdatanaly\banaly2-backend.log 2>&1"
echo    后端启动中, 请等待 30-60 秒...

REM ---- 启动前端 ----
echo.
echo [3/3] 启动前端 (端口 5173)...
cd /d D:\massdatanaly\my-vue-project
start "banaly2-frontend" /MIN cmd /c "npm run serve > D:\massdatanaly\banaly2-frontend.log 2>&1"
echo    前端启动中, 请等待 30-60 秒...

echo.
echo ============================================
echo   启动完成
echo ============================================
echo   前端地址: http://localhost:5173/
echo   后端地址: http://localhost:8088/
echo   日志: D:\massdatanaly\banaly2-*.log
echo ============================================
echo.
timeout /t 8 >nul
start http://localhost:5173/
exit
