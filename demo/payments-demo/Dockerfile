# Local demo image only (demo/up.sh builds it as bpo-payments-demo:local; it is never pushed).
FROM eclipse-temurin:23-jre@sha256:4972459272d4050ab14700677ac7a7511194f1d9ed333b95e886c3c8c80dedae
WORKDIR /app
COPY build/libs/payments-demo.jar /app/payments-demo.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.io.tmpdir=/tmp", "-jar", "/app/payments-demo.jar"]
