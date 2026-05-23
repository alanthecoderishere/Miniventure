@echo off
echo Compiling MiniStudy for Windows...
if not exist bin mkdir bin
dir /s /B src\*.java > sources.txt
javac -d bin -cp "lib/*" @sources.txt
del sources.txt
echo Running MiniStudy...
java --enable-native-access=ALL-UNNAMED -cp "bin;lib/*" com.miniv.core.Main
pause
