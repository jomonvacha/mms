#!/bin/bash

# Stop the Member Management System (mms-service + mms-ui).
# Mirrors the IDFY Studio stop scripts: PID files first, then process-pattern
# and port-based fallbacks for orphans.

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Run from the repo root regardless of where the script is invoked from.
cd "$(dirname "$0")"

MMS_PORT="${SERVER_PORT:-8081}"
MMS_UI_PORT=3001

echo ""
echo -e "${BLUE}Stopping MMS services...${NC}"
echo "════════════════════════════════════════"

stop_by_pid() {
    local name=$1 pid_file=$2
    if [ -f "$pid_file" ]; then
        local pid
        pid=$(cat "$pid_file")
        if ps -p "$pid" >/dev/null 2>&1; then
            echo -e "${YELLOW}Stopping $name (PID: $pid)...${NC}"
            kill "$pid" 2>/dev/null
            sleep 2
            if ps -p "$pid" >/dev/null 2>&1; then
                kill -9 "$pid" 2>/dev/null
            fi
            echo -e "${GREEN}✓ $name stopped${NC}"
        else
            echo -e "${YELLOW}$name was not running${NC}"
        fi
        rm -f "$pid_file"
    else
        echo -e "${YELLOW}No PID file for $name${NC}"
    fi
}

# Stop in reverse order (frontend first, then backend).
stop_by_pid "mms-ui"      logs/mms-ui.pid
stop_by_pid "mms-service" logs/mms-service.pid

# Fallback: kill by process pattern (Vite child workers, detached JVMs).
pkill -f "mms-service.*jar"        2>/dev/null || true
pkill -f "vite.*${MMS_UI_PORT}"    2>/dev/null || true

# Fallback: kill by port (catches orphans when PID files are lost).
# On macOS `ps -o comm=` returns the full executable path (e.g.
# /opt/homebrew/bin/node), so match against the basename to cover macOS + Linux.
for port in "$MMS_PORT" "$MMS_UI_PORT"; do
    for pid in $(lsof -ti :"$port" 2>/dev/null); do
        cmd=$(ps -p "$pid" -o comm= 2>/dev/null)
        base=$(basename "$cmd" 2>/dev/null)
        case "$base" in
            java|node)
                echo -e "${YELLOW}Killing orphaned $base on port $port (PID: $pid)${NC}"
                kill "$pid" 2>/dev/null
                sleep 1
                kill -9 "$pid" 2>/dev/null || true
                ;;
        esac
    done
done

echo ""
echo -e "${GREEN}✓ MMS services stopped${NC}"
echo -e "${YELLOW}  (PostgreSQL container left running — stop with: docker compose down)${NC}"
