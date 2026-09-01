# ---- Build Stage ----
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Gradle wrapper + 설정 먼저 복사 (의존성 캐싱)
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# 이 이미지를 만든 커밋. 배포가 "방금 만든 것이 실제로 떴는지" 확인하는 데 쓴다.
# 값이 없으면 unknown 으로 뜨고, 그때 배포는 확인을 건너뛰지 않고 멈춘다.
ARG GIT_SHA=""
ENV APP_GIT_SHA=$GIT_SHA

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
