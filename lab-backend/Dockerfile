# 多阶段构建：容器内路径全为 ASCII，规避 Windows 中文路径下
# spring-boot-maven-plugin .argfile 编码问题（本地开发请用 subst 方案）
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/target/lab-backend.jar /app/lab-backend.jar
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/lab-backend.jar"]
