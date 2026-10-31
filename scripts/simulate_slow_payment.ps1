# TicketHub - Simulate Slow Payment & Observability Tracing Script
param(
    [int]$LatencyMs = 5000,
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$BookingId = "101"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   TicketHub Observability: Slow Payment Simulation" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$CorrId = [System.Guid]::NewGuid().ToString()
Write-Host "[1] Generated Trace Correlation-ID : $CorrId" -ForegroundColor Yellow
Write-Host "[2] Target Booking-ID              : $BookingId" -ForegroundColor Yellow
Write-Host "[3] Simulated Latency Parameter     : ${LatencyMs}ms" -ForegroundColor Yellow

$Headers = @{
    "X-Correlation-Id" = $CorrId
    "X-Booking-Id"     = $BookingId
    "Content-Type"     = "application/json"
}

$Payload = @{
    bookingId = [int64]$BookingId
    amount = 500
    currency = "INR"
    idempotencyKey = "sim-" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
} | ConvertTo-Json

Write-Host "`n[4] Sending Payment Request through API Gateway ($GatewayUrl/api/payments)..." -ForegroundColor White
$StopWatch = [System.Diagnostics.Stopwatch]::StartNew()

try {
    $Response = Invoke-RestMethod -Uri "$GatewayUrl/api/payments" -Method Post -Headers $Headers -Body $Payload -TimeoutSec 15
    $StopWatch.Stop()
    Write-Host " Payment Response received in $($StopWatch.ElapsedMilliseconds) ms" -ForegroundColor Green
    Write-Host ($Response | ConvertTo-Json -Depth 3)
} catch {
    $StopWatch.Stop()
    Write-Host " Payment request completed/failed in $($StopWatch.ElapsedMilliseconds) ms" -ForegroundColor Red
    Write-Host " Error Details: $_" -ForegroundColor DarkRed
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "   How to Verify in Observability Tools" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "1. Grafana Dashboard (Metrics):" -ForegroundColor Yellow
Write-Host "   Open: http://localhost:3001 (User: admin / Password: admin)"
Write-Host "   Check Panel 'P95 Request Latency' and 'HTTP Errors (4xx & 5xx Rate)'"
Write-Host ""
Write-Host "2. Grafana Loki (Centralized JSON Logs):" -ForegroundColor Yellow
Write-Host "   Open: http://localhost:3001/explore"
Write-Host "   Run Query:" -ForegroundColor Green
Write-Host '   {container=~"tickethub-.*"} | json | correlationId="' + $CorrId + '"' -ForegroundColor Green
Write-Host "   OR filter by bookingId:" -ForegroundColor Green
Write-Host '   {container=~"tickethub-.*"} | json | bookingId="' + $BookingId + '"' -ForegroundColor Green
Write-Host ""
Write-Host "3. Prometheus (Direct Metrics):" -ForegroundColor Yellow
Write-Host "   Open: http://localhost:9090/targets"
Write-Host "   Verify all 6 microservice scrape targets are UP (1/1)"
Write-Host ""
Write-Host "4. Kafka UI (Scenario 0):" -ForegroundColor Yellow
Write-Host "   Open: http://localhost:8090"
Write-Host "   Inspect topics, messages, consumer groups, and lag"
Write-Host "==========================================================" -ForegroundColor Cyan
