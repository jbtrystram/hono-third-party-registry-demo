FROM openjdk:latest
EXPOSE 2626 2727
COPY target/kapuaDeviceRegistry-1.0-SNAPSHOT-fat.jar /maven/
CMD java -jar maven/kapuaDeviceRegistry-1.0-SNAPSHOT-fat.jar
