# Stage 1: Maven으로 WAR 파일 빌드
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Maven 빌드 설정 복사
COPY pom.xml .

# 소스 코드 복사
COPY src ./src

# Maven을 사용하여 WAR 파일 빌드 (테스트는 생략)
RUN mvn package -DskipTests

# Stage 2: Tomcat 10 이미지를 사용하여 애플리케이션 배포
FROM tomcat:10-jre17-temurin

# Tomcat 기본 웹 애플리케이션 삭제
RUN rm -rf /usr/local/tomcat/webapps/*

# 빌드한 WAR 파일을 Tomcat의 webapps 디렉토리에 복사 (ROOT로 배포)
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war

# 애플리케이션이 사용하는 포트 노출
EXPOSE 8080

# Spring 프로필을 'prod'로 설정
ENV SPRING_PROFILES_ACTIVE=prod

# Tomcat 기본 명령어 사용 (catalina.sh run)
CMD ["catalina.sh", "run"]