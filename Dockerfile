FROM maven:3.5.3-jdk-11

COPY . /app
WORKDIR /app
RUN mvn -B -q clean package

FROM openjdk:11-jre-slim
COPY --from=0 /app/target/telepathy*.jar /app/telepathy.jar
COPY --from=0 /app/entrypoint.sh /app/entrypoint.sh

ENTRYPOINT ["sh", "/app/entrypoint.sh"]