# Backend do FilaVIVA (Spring Boot 4, Java 25). Construído a partir da RAIZ do repositório, porque leva junto o
# dataset (data/):  docker build -f backend/Dockerfile -t filaviva-backend .
# A chave da IA NUNCA entra na imagem: ela chega como variável de ambiente na hora de rodar (docker-compose.prod.yml).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# no Windows o Git pode trocar o fim de linha do mvnw; no Linux ele precisa de LF
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src src
RUN ./mvnw -q -B -DskipTests package && cp target/timevermelho-*.jar /app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home filaviva
WORKDIR /app/backend
COPY --from=build /app.jar app.jar
COPY data /app/data
# dataset dentro da imagem; o fila_priorizada.csv oficial já está no repositório, então não é regravado aqui
ENV DATASET_DIR=/app/data \
    EXPORT_ARQUIVO= \
    JAVA_OPTS="-XX:MaxRAMPercentage=60"
USER filaviva
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
