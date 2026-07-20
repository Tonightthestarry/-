@echo off
chcp 65001 >nul
title 启动全部服务 - Hadoop + SpringBoot + Vue

echo ================================================================
echo   一键启动全部服务
echo   Hadoop NameNode + DataNode + 后端 8088 + 前端 5173
echo ================================================================

:: 1. 启动 Hadoop 
echo [1/3] 启动 Hadoop HDFS...
start "Hadoop-DFS" cmd /c "cd /d D:\massdatanaly\hadoop-3.5.0\sbin && start-dfs.cmd"
echo         Hadoop NameNode 9870 + DataNode 9864 启动中 (约30秒)...

:: 2. 启动后端 Spring Boot (8088)
echo [2/3] 启动后端 Spring Boot (8088)...
start "SpringBoot-8088" cmd /c "cd /d D:\massdatanaly\banaly2-java && mvn spring-boot:run"
echo         后端 8088 编译中 (首次约30-60秒, 之后快了)...

:: 3. 启动前端 Vue (5173)
echo [3/3] 启动前端 Vue (5173)...
start "Vue-5173" cmd /c "cd /d D:\massdatanaly\my-vue-project && npm run serve"
echo         前端 5173 启动中 (约10秒)...

echo.
echo ================================================================
echo   三个服务已全部启动!
echo.
echo   Hadoop NameNode: http://localhost:9870
echo   后端 SpringBoot: http://localhost:8088
echo   前端 Vue:        http://localhost:5173
echo.
echo   约 60 秒后, 浏览器打开 http://localhost:5173
echo   登录: admin / 123456
echo ================================================================
echo.

:: 60秒后自动打开浏览器
timeout /t 60 /nobreak >nul
start http://localhost:5173
