# =====================================================
#  MessageOnline Chat Server — Docker образ
#  Двухэтапная сборка: сначала компилируем, потом запускаем
# =====================================================

# ── ЭТАП 1: Сборка (Maven + JDK 17) ─────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Сначала копируем только pom.xml — Docker кэширует зависимости
# Повторная сборка не скачивает зависимости заново если pom.xml не изменился
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Теперь копируем исходный код и собираем JAR
COPY src ./src
RUN mvn package -q -DskipTests

# ── ЭТАП 2: Запуск (только JRE, без Maven — образ меньше) ──
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Копируем только JAR из этапа сборки
COPY --from=builder /app/target/chat-server.jar ./chat-server.jar

# Создаём директорию для SQLite базы данных
# При Fly.io сюда монтируется постоянный том (persistent volume)
RUN mkdir -p /data

# Порт TCP сокета
EXPOSE 8888

# Переменные окружения
ENV SERVER_PORT=8888
ENV DB_PATH=/data/chat.db

# Запуск сервера
CMD ["java", "-jar", "chat-server.jar"]
