# Production image: frontend (Vite build) + backend (AOT uberjar) on a JRE.
#   docker build -t auksjon .
#   docker run -p 3000:3000 -v auksjon-data:/data --env-file backend/.env auksjon

# --- frontend ---------------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- backend ----------------------------------------------------------------
FROM clojure:temurin-21-tools-deps AS backend
WORKDIR /src
COPY backend/deps.edn ./
RUN clojure -P && clojure -P -T:build
COPY backend/build.clj ./
COPY backend/src src
COPY backend/resources resources
RUN clojure -T:build uber

# --- runtime ----------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app auksjon \
 && mkdir -p /data && chown auksjon /data
WORKDIR /app
COPY --from=backend /src/target/auksjon.jar auksjon.jar
COPY --from=frontend /src/dist static
USER auksjon
ENV PORT=3000 \
    STATIC_DIR=/app/static \
    DB_URL=jdbc:sqlite:/data/auksjon.db \
    JAVA_TOOL_OPTIONS="-Xmx128m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
VOLUME /data
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/auksjon.jar"]
