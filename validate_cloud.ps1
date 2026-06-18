# Script de validacao para o ambiente Cloud Simulation

function Test-Endpoint {
    param($url, $name)
    Write-Host "A testar $name ($url)..." -ForegroundColor Cyan
    try {
        $resp = Invoke-RestMethod -Uri $url -Method Get -ErrorAction Stop
        Write-Host "  [OK] Status: $($resp.status)" -ForegroundColor Green
    } catch {
        Write-Host "  [ERRO] Falha ao aceder $name : $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n--- Validacao de Rotas Cloud (via Ingress) ---`n" -ForegroundColor Yellow

# Note que agora usamos a porta 80 para TUDO, variando apenas o PATH
Test-Endpoint "http://localhost/api/v1/auth/health" "JWT Manager"
Test-Endpoint "http://localhost/api/v1/training/health" "Training Service"
Test-Endpoint "http://localhost/api/v1/analysis/health" "Analysis Service"

Write-Host "`n--- Verificacao de Documentacao (Swagger) ---" -ForegroundColor Yellow
Write-Host "JWT Docs: http://localhost/api/v1/auth/docs"
Write-Host "Training Docs: http://localhost/api/v1/training/docs"
Write-Host "Analysis Docs: http://localhost/api/v1/analysis/docs"
