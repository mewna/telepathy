FROM maven:3

COPY . /app
WORKDIR /app
RUN mvn -B -q clean package

FROM openjdk:8-jre-alpine
COPY --from=0 /app/target/telepathy*.jar /app/telepathy.jar
RUN apk update && apk add nginx
RUN mkdir -pv /etc/nginx/conf.d
COPY ./telepathy.conf /etc/nginx/conf.d/telepathy.conf

ENTRYPOINT ["sh", "/app/entrypoint.sh"]