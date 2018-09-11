FROM openjdk:latest
EXPOSE 5672
COPY target/kapuaDeviceRegistry-1.0-SNAPSHOT-fat.jar /maven/
CMD java -jar maven/kapuaDeviceRegistry-1.0-SNAPSHOT-fat.jar
