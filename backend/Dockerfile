# Compilação e execução em imagens separadas: a imagem final não leva o Maven
# nem o código-fonte, o que a torna muito mais pequena e reduz a superfície de
# ataque de quem a correr.

# ---- Compilação ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# As dependências mudam muito menos vezes do que o código: copiar o pom sozinho
# primeiro faz o Docker reaproveitar a camada e evita descarregar tudo outra vez
# a cada alteração de uma classe.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Execução --------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Utilizador sem privilégios: um processo comprometido não deve ser root.
RUN addgroup -S autocare && adduser -S autocare -G autocare \
    && mkdir -p /var/lib/autocare/files \
    && chown -R autocare:autocare /var/lib/autocare

COPY --from=build /build/target/*.jar app.jar
USER autocare

# Os ficheiros carregados (fotografias, documentos) vivem fora do contentor;
# sem isto perdem-se a cada nova versão.
VOLUME ["/var/lib/autocare/files"]

EXPOSE 8080

# MaxRAMPercentage em vez de -Xmx fixo: o limite passa a acompanhar o que o
# contentor tiver, sem ter de recompilar a imagem para mudar de máquina.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
