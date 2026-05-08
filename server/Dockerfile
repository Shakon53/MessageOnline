# =====================================================
#  MessageOnline Chat Server — Docker образ
#  Двухэтапная сборка: сначала компилируем, потом запускаем
#
#  Собирается из КОРНЯ репозитория:
#    docker build -f server/Dockerfile .
#  Или из папки server/ (локально):
#    docker build .
# =====================================================

# ── ЭТАП 1: Сборка (Maven + JDK 17) ─────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Пробуем скопировать pom.xml из server/ (Railway, корень репо)
# или из текущей папки (локальная сборка из server/)
COPY server/pom.xml ./pom.xml

RUN mvn dependency:go-offline -q

COPY server/src ./src
RUN mvn package -q -DskipTests

# ── ЭТАП 2: Запуск (только JRE, без Maven) ──────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/chat-server.jar ./chat-server.jar

RUN mkdir -p /data

EXPOSE 8888

ENV SERVER_PORT=8888
ENV DB_PATH=/data/chat.db

CMD ["java", "-jar", "chat-server.jar"]
