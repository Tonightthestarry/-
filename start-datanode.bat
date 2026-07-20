@echo off
set HADOOP_HOME=D:\massdatanaly\hadoop-3.5.0
set HADOOP_USER_NAME=Administrator
cd /d D:\massdatanaly\hadoop-3.5.0
bin\hdfs.cmd datanode > logs\datanode-startup.log 2>&1
