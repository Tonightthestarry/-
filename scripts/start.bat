@echo off
chcp 65001 >nul
title Banaly2 城市级多源海量数据智能挖掘平台
color 0A

echo ============================================================
echo   城市级多源海量数据智能挖掘与可视化决策平台
echo   Banaly2 - 一键启动脚本
echo ============================================================
echo.

REM ===== 1. 设置 Java 环境 =====
set JAVA_HOME=E:\jdk\jdk17
set PATH=%JAVA_HOME%\bin;%PATH%
echo [1/5] 检查 Java 环境...
java -version 2>&1
if errorlevel 1 (
    echo   X 找不到 Java，请先安装 JDK 17
    pause
    exit /b 1
)
echo.

REM ===== 2. 设置 Maven 环境（优先用 IDEA 自带的）=====
set MAVEN_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    set MAVEN_HOME=C:\apache-maven-3.9.6
)
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    set MAVEN_HOME=C:\Program Files\Apache\Maven\apache-maven-3.9.6
)
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [2/5] X 找不到 Maven,请确认安装位置
    echo   IDEA 自带位置: C:\Program Files\JetBrains\IntelliJ IDEA 2024.3\plugins\maven\lib\maven3
    echo   或独立安装:    C:\apache-maven-3.9.6
    echo.
    pause
    exit /b 1
)
set PATH=%MAVEN_HOME%\bin;%PATH%
echo [2/5] 检查 Maven 环境...
"%MAVEN_HOME%\bin\mvn.cmd" -version
echo.

REM ===== 3. 启动 MongoDB =====
echo [3/5] 启动 MongoDB...
net start MongoDB 2>nul
if errorlevel 1 (
    echo   ! MongoDB 启动失败,尝试手动方式...
    if exist "C:\Program Files\MongoDB\Server\7.0\bin\mongod.exe" (
        start "MongoDB" "C:\Program Files\MongoDB\Server\7.0\bin\mongod.exe" --dbpath "C:\data\db"
    )
)
echo.

REM ===== 4. 启动 HDFS =====
echo [4/5] 启动 HDFS (Hadoop)...
if exist "D:\massdatanaly\hadoop-3.5.0\sbin\start-dfs.cmd" (
    call "D:\massdatanaly\hadoop-3.5.0\sbin\start-dfs.cmd"
) else (
    echo   ! HDFS 启动脚本未找到,跳过
)
echo.

REM ===== 5. 启动 Spring Boot 应用 =====
echo [5/5] 启动 Banaly2 平台...
echo.
echo   访问地址: http://localhost:8088
echo   账号: admin / 123456
echo   第一次启动较慢(约 30-60 秒)
echo.
echo ============================================================
echo.

cd /d d:\massdatanaly\banaly2-java
"%MAVEN_HOME%\bin\mvn.cmd" spring-boot:run

pause
