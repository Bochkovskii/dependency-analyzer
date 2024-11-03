# dependency-analyzer
Project for determining, whether it is possible 
to run a class with a classpath, formed
from a list of jar files.

## Usage:
1) clone the repository
2) build using
```./gradlew build```
3) executable .jar will appear at 
build/libs/dependency-analyzer-1.0-all.jar
4) run with
```./gradlew run --args="com.example.Class path1/file.jar path2/file.jar ..."```
