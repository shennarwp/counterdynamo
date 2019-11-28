FROM openjdk:8-jre-slim

# kopiere die kompilierte jar datei
COPY target/counter-dynamo*with-dependencies.jar /counterdynamo.jar

CMD java -jar /counterdynamo.jar
