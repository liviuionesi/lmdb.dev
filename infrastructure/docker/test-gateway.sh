#!/bin/bash

# Filmpire API Gateway Testing Script
# Tests all major gateway features including DDoS protection

set -e

GATEWAY_URL="http://localhost:8080"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Filmpire API Gateway - Testing Suite                ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if gateway is running
echo -e "${BLUE}[1/10]${NC} Checking gateway health..."
if curl -s -f "$GATEWAY_URL/actuator/health" > /dev/null; then
    echo -e "${GREEN}✓${NC} Gateway is running"
else
    echo -e "${RED}✗${NC} Gateway is not responding. Is it running?"
    echo "   Start with: cd infrastructure/docker && docker compose up -d"
    exit 1
fi
echo ""

# Test 1: Health Check
echo -e "${BLUE}[2/10]${NC} Health Check..."
HEALTH=$(curl -s "$GATEWAY_URL/actuator/health" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
if [ "$HEALTH" = "UP" ]; then
    echo -e "${GREEN}✓${NC} Status: $HEALTH"
else
    echo -e "${YELLOW}⚠${NC} Status: $HEALTH"
fi
echo ""

# Test 2: Routes Configuration
echo -e "${BLUE}[3/10]${NC} Routes Configuration..."
ROUTE_COUNT=$(curl -s "$GATEWAY_URL/actuator/gateway/routes" | jq 'length' 2>/dev/null || echo "0")
if [ "$ROUTE_COUNT" -ge 6 ]; then
    echo -e "${GREEN}✓${NC} $ROUTE_COUNT routes configured"
else
    echo -e "${YELLOW}⚠${NC} Only $ROUTE_COUNT routes found (expected 6+)"
fi
echo ""

# Test 3: Rate Limiting Headers
echo -e "${BLUE}[4/10]${NC} Rate Limiting Headers..."
RATE_LIMIT_HEADERS=$(curl -s -I "$GATEWAY_URL/api/v1/movies" 2>&1 | grep -i "X-RateLimit" | wc -l)
if [ "$RATE_LIMIT_HEADERS" -gt 0 ]; then
    echo -e "${GREEN}✓${NC} Rate limit headers present"
    curl -s -I "$GATEWAY_URL/api/v1/movies" | grep -i "X-RateLimit" | head -3
else
    echo -e "${YELLOW}⚠${NC} Rate limit headers not found"
fi
echo ""

# Test 4: Normal Request (Under Rate Limit)
echo -e "${BLUE}[5/10]${NC} Normal Request (Under Rate Limit)..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/movies")
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "503" ]; then
    echo -e "${GREEN}✓${NC} Request processed (HTTP $HTTP_CODE)"
    echo "   Note: 503 is OK if downstream services aren't running"
else
    echo -e "${YELLOW}⚠${NC} Unexpected status: HTTP $HTTP_CODE"
fi
echo ""

# Test 5: Rate Limiting (Rapid Requests)
echo -e "${BLUE}[6/10]${NC} Rate Limiting Test (sending 110 requests)..."
echo "   This may take a few seconds..."
SUCCESS_COUNT=0
RATE_LIMITED=0

for i in {1..110}; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/movies")
    if [ "$HTTP_CODE" = "429" ]; then
        RATE_LIMITED=$((RATE_LIMITED + 1))
    elif [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "503" ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    fi
done

if [ "$RATE_LIMITED" -gt 0 ]; then
    echo -e "${GREEN}✓${NC} Rate limiting working!"
    echo "   Successful: $SUCCESS_COUNT, Rate Limited: $RATE_LIMITED"
else
    echo -e "${YELLOW}⚠${NC} No rate limiting detected (may need more requests)"
fi
echo ""

# Test 6: Auth Endpoint Rate Limiting
echo -e "${BLUE}[7/10]${NC} Auth Endpoint Rate Limiting..."
AUTH_RATE_LIMITED=0
for i in {1..15}; do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$GATEWAY_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"test","password":"test"}')
    if [ "$HTTP_CODE" = "429" ]; then
        AUTH_RATE_LIMITED=$((AUTH_RATE_LIMITED + 1))
    fi
done

if [ "$AUTH_RATE_LIMITED" -gt 0 ]; then
    echo -e "${GREEN}✓${NC} Auth rate limiting active ($AUTH_RATE_LIMITED requests blocked)"
else
    echo -e "${YELLOW}⚠${NC} Auth rate limiting not detected"
fi
echo ""

# Test 7: CORS Headers
echo -e "${BLUE}[8/10]${NC} CORS Configuration..."
CORS_HEADERS=$(curl -s -I -X OPTIONS "$GATEWAY_URL/api/v1/movies" \
    -H "Origin: http://localhost:3000" \
    -H "Access-Control-Request-Method: GET" | grep -i "Access-Control" | wc -l)
if [ "$CORS_HEADERS" -gt 0 ]; then
    echo -e "${GREEN}✓${NC} CORS headers present"
else
    echo -e "${YELLOW}⚠${NC} CORS headers not found"
fi
echo ""

# Test 8: Circuit Breaker (Fallback)
echo -e "${BLUE}[9/10]${NC} Circuit Breaker Fallback..."
FALLBACK=$(curl -s "$GATEWAY_URL/fallback/movies" | jq -r '.message' 2>/dev/null || echo "")
if [ -n "$FALLBACK" ]; then
    echo -e "${GREEN}✓${NC} Fallback endpoint working"
    echo "   Message: $FALLBACK"
else
    echo -e "${YELLOW}⚠${NC} Fallback endpoint not responding"
fi
echo ""

# Test 9: Request Size Limit (if possible)
echo -e "${BLUE}[10/10]${NC} Request Size Limits..."
echo "   (Skipped - requires large file upload)"
echo ""

# Summary
echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                    Test Summary                          ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}Gateway URL:${NC} $GATEWAY_URL"
echo -e "${GREEN}Eureka Dashboard:${NC} http://localhost:8761"
echo -e "${GREEN}Redis Commander:${NC} http://localhost:9083"
echo ""
echo -e "${BLUE}Next Steps:${NC}"
echo "  1. Check Eureka dashboard: http://localhost:8761"
echo "  2. View gateway logs: docker compose logs -f api-gateway"
echo "  3. Test IP blacklist: See TESTING_GUIDE.md"
echo ""
echo -e "${GREEN}✅ Testing complete!${NC}"


