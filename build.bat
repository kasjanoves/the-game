@echo off
setlocal

REM Define the source file directory and the output directory
set SRC_DIR=src
set OUT_DIR=out

REM Define the main class for the JAR manifest
set MAIN_CLASS=game.TheGameApp

REM Define the name of the JAR file
set JAR_NAME=GameServer.jar

REM Create the output directory if it doesn't exist
if not exist %OUT_DIR% mkdir %OUT_DIR%

REM Compile the Java files into the output directory
for /r %SRC_DIR% %%f in (*.java) do (
    javac -sourcepath %SRC_DIR% -d %OUT_DIR% %%f
)

REM Create the JAR file
echo Main-Class: %MAIN_CLASS% > manifest.txt
jar cvfm %JAR_NAME% manifest.txt -C %OUT_DIR% .

REM Clean up the manifest file
del manifest.txt

echo Build complete: %JAR_NAME%

endlocal