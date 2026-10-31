# ==============================================================================
# TicketHub Phase 2 - Scenario 11: Consumer Lag Simulation Script
# ==============================================================================
# Generates a burst of events on the Kafka topic to demonstrate consumer lag growth
# and monitors the lag drain.
# ==============================================================================

param (
    [int]$BurstCount = 50,
    [string]$Topic = "tickethub.booking.confirmed",
    [string]$BootstrapServer = "localhost:9092"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 🚀 TicketHub Scenario 11: Consumer Lag Simulation" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Topic      : $Topic"
Write-Host "Burst Count       : $BurstCount messages"
Write-Host "Kafka Server      : $BootstrapServer"
Write-Host ""

# Ensure curl / Invoke-RestMethod is available
$notificationUrl = "http://localhost:8085/api/notifications/demo"

Write-Host "Publishing burst of $BurstCount messages to trigger lag..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 1; $i -le $BurstCount; $i++) {
    $email = "burst_tester_$i@example.com"
    try {
        $resp = Invoke-RestMethod -Uri "$notificationUrl`?email=$email&name=LagTester$i" -Method Post -TimeoutSec 3
        Write-Host "." -NoNewline -ForegroundColor Green
    } catch {
        Write-Host "x" -NoNewline -ForegroundColor Red
    }
    if ($i % 25 -eq 0) {
        Write-Host " [$i / $BurstCount]" -ForegroundColor Gray
    }
}
$sw.Stop()
Write-Host ""
Write-Host "Burst publish completed in $($sw.ElapsedMilliseconds) ms." -ForegroundColor Green
Write-Host ""

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 📊 Monitoring Consumer Lag" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "1. View live consumer lag in Grafana Dashboard: http://localhost:3001" -ForegroundColor Yellow
Write-Host "   (Panel: Kafka & Consumer Lag -> Notification Consumer Lag)"
Write-Host "2. Inspect topic partitions and lag in Kafka UI: http://localhost:8090" -ForegroundColor Yellow
Write-Host "   (Topic: $Topic | Consumer Group: notification-service-group)"
Write-Host ""
Write-Host "Simulation complete." -ForegroundColor Cyan
