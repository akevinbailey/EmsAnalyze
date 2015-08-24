#!/bin/sh

MY_JAVA=

if [ -z $MY_JAVA ]
then
   MY_JAVA=java
fi

$MY_JAVA -Xmx32m -jar EmsAnalyze.jar -connect tcp://localhost:7222 -user myuser -password mypass -clientId Tester -reqDest my.request -msgSize 2048 -delay 1000 -logFile ./emsanalyze.csv