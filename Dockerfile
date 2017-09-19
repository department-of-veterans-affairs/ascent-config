FROM java:8

ADD target/ascent-config-*.jar /ascent-config.jar
ENTRYPOINT ["java", "-Xms32m", "-Xmx256m", "-jar", "/ascent-config.jar"]
