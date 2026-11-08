# 1단계: 빌드 (JDK + Maven Wrapper)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
# COPY를 두번 나눈 이유: 도커는 각 줄의 결과를 캐시해 두고, 그 줄에 들어가는 파일이 바뀌지 않으면 다시 실행하지 않는다.
# 아래와 같이 두 단계로 나누면 pom.xml이 바뀌얼을 때 1,2단계가 실행되고 소스만 바뀌었을 때는 오래걸리는 1단계가 생략된다.
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

# 2단계: 실행 (JRE만)
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Seoul
# root가 아닌 일반 사용자로 실행. 앱에 취약점이 있어도 컨테이너 안에서 root 권한을 얻지 못함
RUN useradd --system --no-create-home app
COPY --from=build /app/target/chat-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
