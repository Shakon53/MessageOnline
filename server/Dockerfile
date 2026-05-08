FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -q -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/chat-server.jar ./chat-server.jar
RUN mkdir -p /data
EXPOSE 8888
ENV SERVER_PORT=8888
ENV DB_PATH=/data/chat.db
CMD ["java", "-jar", "chat-server.jar"]
