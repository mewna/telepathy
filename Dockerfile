FROM maven:3

COPY . /app
WORKDIR /app
RUN mvn -B -q clean package

FROM openjdk:8-jre-alpine
COPY --from=0 /app/target/telepathy*.jar /app/telepathy.jar
COPY --from=0 /app/entrypoint.sh /app/entrypoint.sh
RUN apk update && apk add nginx
RUN mkdir -pv /etc/nginx/conf.d
RUN mkdir -pv /run/nginx
COPY default.conf /etc/nginx/conf.d/default.conf

ENTRYPOINT ["sh", "/app/entrypoint.sh"]