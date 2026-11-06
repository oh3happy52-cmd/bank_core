# [무엇] 앱(bank_core)을 Docker 이미지(상자)로 만드는 설명서
# [역할] 1단계(build): JDK로 소스를 컴파일해서 실행 파일(jar)을 만듦
#        2단계(run): 실행에 필요한 JRE와 jar만 담아 가벼운 이미지를 만듦 (컴파일 도구·소스는 버림)
# [누가 쓰나] docker-compose.prod.yml이 서버(EC2)에서 이미지를 만들 때 사용 (docker compose up --build)

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle 설정 파일을 먼저 복사해서 라이브러리를 미리 받아 둠
# → 소스만 바뀌었을 때는 이 단계를 Docker가 재사용(캐시)해서 다시 빌드가 빨라짐
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# 소스를 복사하고 실행 파일(jar) 만들기
# 테스트는 CI(GitHub Actions)가 이미 돌리므로 여기서는 건너뜀 (-x test)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 1단계에서 만든 jar만 가져옴
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# -Duser.timezone=Asia/Seoul : 한국 시간으로 시작 (CI 때 겪은 시간대 문제와 같은 이유로 JVM 시작 시점에 고정)
# -XX:MaxRAMPercentage=60    : 컨테이너 메모리의 60%까지만 Java가 쓰게 함 (같은 서버의 MySQL 몫을 남김)
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
