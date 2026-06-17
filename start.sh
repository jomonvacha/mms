#!/bin/bash

# Start the Member Management System locally:
#   1. PostgreSQL   (local Docker container — only when DATABASE_URL points at localhost)
#   2. mms-service  (Spring Boot API — port 8081)
#   3. mms-ui       (Vite dev server — port 3001, proxies /api → 8081)
#
# Mirrors the IDFY Studio start scripts: nohup background processes, PID files
# under logs/, health-checked startup, and a summary box at the end.

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Run from the repo root regardless of where the script is invoked from.
cd "$(dirname "$0")"

MMS_PORT="${SERVER_PORT:-8081}"
MMS_UI_PORT=3001

mkdir -p logs

echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║          Member Management System — Local Startup            ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ── Prerequisites ─────────────────────────────────────────────────────────────

check_command() {
    if ! command -v "$1" &>/dev/null; then
        echo -e "${RED}✗ $1 is not installed.${NC} $2"
        exit 1
    fi
}

check_command java "Install Java 25+."
check_command mvn  "Install Maven 3.9+ (brew install maven)."
check_command node "Install Node.js 20+."
check_command npm  "Comes with Node.js."

JAVA_VER=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
NODE_VER=$(node --version | tr -d 'v' | cut -d. -f1)
echo -e "${GREEN}✓ Prerequisites: Java ${JAVA_VER}, Node ${NODE_VER}, Maven${NC}"

# Suppress Lombok sun.misc.Unsafe warnings on Java 25+
export MAVEN_OPTS="${MAVEN_OPTS} --add-opens java.base/sun.misc=ALL-UNNAMED"

# ── Load .env ─────────────────────────────────────────────────────────────────

# Parse KEY=VALUE pairs without invoking the shell on the value, so unquoted
# values can safely contain shell specials like `&` (e.g. a Neon JDBC URL with
# `?sslmode=require&channel_binding=require`). Surrounding quotes are stripped;
# blank lines and `#` comments are skipped. Real shell env wins over .env.
load_env_file() {
    local file="$1"
    local key value
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        line="${line#"${line%%[![:space:]]*}"}"
        [ -z "$line" ] && continue
        case "$line" in \#*) continue ;; esac
        case "$line" in [A-Za-z_]*=*) ;; *) continue ;; esac
        key="${line%%=*}"
        value="${line#*=}"
        case "$value" in
            \"*\") value="${value#\"}"; value="${value%\"}" ;;
            \'*\') value="${value#\'}"; value="${value%\'}" ;;
        esac
        if [ -z "${!key+x}" ]; then
            export "$key=$value"
        fi
    done < "$file"
}
for env_file in .env.local .env; do
    if [ -f "$env_file" ]; then
        echo -e "${BLUE}Loading environment from ${env_file}${NC}"
        load_env_file "$env_file"
        break
    fi
done

# ── Resolve database connection ───────────────────────────────────────────────

# Prefer an explicit JDBC URL (DATABASE_URL / MMS_DATABASE_URL). Otherwise derive
# a localhost URL from the docker-compose-style settings in .env.example so the
# shipped defaults "just work" against the local Postgres container.
DATABASE_URL="${DATABASE_URL:-${MMS_DATABASE_URL}}"
if [ -z "${DATABASE_URL}" ]; then
    DATABASE_URL="jdbc:postgresql://localhost:${DATABASE_PORT:-5432}/${DATABASE_NAME:-roots_mms}"
    echo -e "${YELLOW}DATABASE_URL not set — defaulting to ${DATABASE_URL}${NC}"
fi
export DATABASE_URL
export DATABASE_USERNAME="${DATABASE_USERNAME:-mms}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-mms}"

if [ -z "${JWT_SECRET}" ]; then
    echo -e "${RED}✗ JWT_SECRET is not set.${NC}"
    echo -e "${YELLOW}  Copy .env.example to .env and set a strong value:${NC}"
    echo -e "${YELLOW}    JWT_SECRET=\$(openssl rand -base64 64)${NC}"
    exit 1
fi
if [ -z "${ADMIN_PASSWORD}" ]; then
    echo -e "${YELLOW}⚠ ADMIN_PASSWORD is not set — the admin user will not be seeded on first run.${NC}"
fi
echo -e "${GREEN}✓ Environment loaded${NC}"

# ── Local PostgreSQL (only when pointing at localhost) ────────────────────────

port_in_use() { lsof -Pi :"$1" -sTCP:LISTEN -t >/dev/null 2>&1; }
port_process() { lsof -Pi :"$1" -sTCP:LISTEN 2>/dev/null | tail -1 | awk '{print $1 " (PID " $2 ")"}'; }

case "$DATABASE_URL" in
    *@localhost*|*//localhost*|*//127.0.0.1*)
        DB_PORT="${DATABASE_PORT:-5432}"
        if command -v docker &>/dev/null; then
            # Don't trust a bare listening socket on $DB_PORT — editor port
            # forwarders (e.g. VS Code's "Code Helper") hold common dev ports
            # open without serving anything, which masks a missing database.
            # The compose container + pg_isready is the real readiness signal.
            echo -e "${BLUE}Ensuring local PostgreSQL is up (docker compose up -d postgres)...${NC}"
            if ! docker compose up -d postgres >/dev/null 2>&1; then
                echo -e "${RED}✗ Could not start the postgres container.${NC}"
                echo -e "${YELLOW}  Port $DB_PORT may be held by another process: $(port_process "$DB_PORT")${NC}"
                echo -e "${YELLOW}  Free it and retry, or point DATABASE_URL at an external database.${NC}"
                exit 1
            fi
            echo -ne "  ${YELLOW}Waiting for PostgreSQL${NC}"
            db_ready=0
            for _ in $(seq 1 30); do
                if docker compose exec -T postgres pg_isready -q >/dev/null 2>&1; then
                    echo -e "\r  ${GREEN}✓ PostgreSQL is ready                    ${NC}"; db_ready=1; break
                fi
                echo -n "."; sleep 1
            done
            [ "$db_ready" -eq 1 ] || echo -e "\r  ${YELLOW}⚠ PostgreSQL not confirmed ready — continuing anyway${NC}"
        elif port_in_use "$DB_PORT"; then
            echo -e "${GREEN}✓ Assuming PostgreSQL is available on port $DB_PORT${NC}"
        else
            echo -e "${YELLOW}⚠ PostgreSQL is not running on port $DB_PORT and Docker is unavailable.${NC}"
            echo -e "${YELLOW}  Start one manually, then re-run this script.${NC}"
        fi
        ;;
    *)
        echo -e "${GREEN}✓ Using external database${NC}"
        ;;
esac

# ── Helpers ───────────────────────────────────────────────────────────────────

wait_for() {
    local url=$1 name=$2 attempt=1 max=40
    echo -ne "  ${YELLOW}Waiting for $name${NC}"
    while [ $attempt -le $max ]; do
        if curl -s "$url" >/dev/null 2>&1; then
            echo -e "\r  ${GREEN}✓ $name is ready                              ${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    echo -e "\r  ${RED}✗ $name did not start within $(( max * 2 ))s${NC}"
    return 1
}

# ── Step 1: mms-service ───────────────────────────────────────────────────────

echo ""
echo -e "${CYAN}[1/2] mms-service (auth + member management — port $MMS_PORT)${NC}"

if curl -s -o /dev/null --max-time 3 "http://localhost:$MMS_PORT/actuator/health"; then
    echo -e "  ${YELLOW}Already running and healthy on port $MMS_PORT — skipping${NC}"
elif port_in_use "$MMS_PORT"; then
    echo -e "  ${RED}✗ Port $MMS_PORT is held by another process (not mms-service):${NC}"
    echo -e "  ${YELLOW}    $(port_process "$MMS_PORT")${NC}"
    echo -e "  ${YELLOW}  Free it (e.g. remove the VS Code forwarded port) and re-run.${NC}"
    exit 1
else
    echo -e "  ${BLUE}Building...${NC}"
    mvn package -pl mms-service -am -DskipTests -q

    echo -e "  ${BLUE}Starting mms-service...${NC}"
    SERVER_PORT="$MMS_PORT" \
    nohup java -jar mms-service/target/mms-service-*.jar \
        > logs/mms-service.log 2>&1 &
    echo $! > logs/mms-service.pid

    if wait_for "http://localhost:$MMS_PORT/actuator/health" "mms-service"; then
        echo -e "  ${GREEN}✓ mms-service started (PID: $(cat logs/mms-service.pid))${NC}"
    else
        echo -e "  ${RED}✗ mms-service failed — check logs/mms-service.log${NC}"
        exit 1
    fi
fi

# ── Step 2: mms-ui ────────────────────────────────────────────────────────────

echo ""
echo -e "${CYAN}[2/2] mms-ui (admin frontend — port $MMS_UI_PORT)${NC}"

if curl -s -o /dev/null --max-time 3 "http://localhost:$MMS_UI_PORT"; then
    echo -e "  ${YELLOW}Already running on port $MMS_UI_PORT — skipping${NC}"
elif port_in_use "$MMS_UI_PORT"; then
    echo -e "  ${RED}✗ Port $MMS_UI_PORT is held by another process (not mms-ui):${NC}"
    echo -e "  ${YELLOW}    $(port_process "$MMS_UI_PORT")${NC}"
    echo -e "  ${YELLOW}  Free it (e.g. remove the VS Code forwarded port) and re-run.${NC}"
    exit 1
else
    cd mms-ui
    if [ ! -d node_modules ]; then
        echo -e "  ${YELLOW}Installing dependencies...${NC}"
        npm install --silent
    fi
    nohup npm run dev > ../logs/mms-ui.log 2>&1 &
    echo $! > ../logs/mms-ui.pid
    cd ..

    if wait_for "http://localhost:$MMS_UI_PORT" "mms-ui"; then
        echo -e "  ${GREEN}✓ mms-ui started (PID: $(cat logs/mms-ui.pid))${NC}"
    else
        echo -e "  ${RED}✗ mms-ui failed — check logs/mms-ui.log${NC}"
        exit 1
    fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────

W=62  # inner width of the box

row() {
    local label="$1" value="$2" color="${3:-$GREEN}"
    local content
    content=$(printf "  %-18s %s" "$label" "$value")
    local pad=$((W - ${#content}))
    printf "${BLUE}║${NC}%s%*s${BLUE}║${NC}\n" "$content" "$pad" ""
}

echo ""
echo -e "${BLUE}╔$(printf '═%.0s' $(seq 1 $W))╗${NC}"
echo -e "${BLUE}║$(printf '%*s' $(( (W + 18) / 2 )) "MMS is running")$(printf '%*s' $(( (W - 18) / 2 )) "")║${NC}"
echo -e "${BLUE}╠$(printf '═%.0s' $(seq 1 $W))╣${NC}"
row "UI"           "http://localhost:$MMS_UI_PORT"
row "Service"      "http://localhost:$MMS_PORT"
row "Health"       "http://localhost:$MMS_PORT/actuator/health"
row "Swagger"      "http://localhost:$MMS_PORT/swagger-ui.html"
echo -e "${BLUE}╠$(printf '═%.0s' $(seq 1 $W))╣${NC}"
row "Logs"         "tail -f logs/*.log" "$YELLOW"
row "Stop"         "./stop.sh" "$YELLOW"
echo -e "${BLUE}╚$(printf '═%.0s' $(seq 1 $W))╝${NC}"

if command -v open &>/dev/null; then
    sleep 1 && open "http://localhost:$MMS_UI_PORT"
fi
