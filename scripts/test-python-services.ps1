param(
    [string]$BaseUrl = "http://localhost",
    [switch]$ImportLeagueDb,
    [switch]$StartTrainingJob
)

$ErrorActionPreference = "Stop"

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )

    $uri = "$BaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri
    }

    return Invoke-RestMethod `
        -Method $Method `
        -Uri $uri `
        -ContentType "application/json" `
        -Body ($Body | ConvertTo-Json -Depth 50)
}

Write-Host "Checking health endpoints..."
Invoke-Json GET "/api/v1/training/health" | Out-Null
Invoke-Json GET "/api/v1/analysis/health" | Out-Null

Write-Host "Checking OpenAPI endpoints..."
$trainingSpec = Invoke-Json GET "/api/v1/training/openapi.json"
Invoke-Json GET "/api/v1/analysis/openapi.json" | Out-Null

if ($trainingSpec.paths.PSObject.Properties.Name -contains "/datasets") {
    throw "Training OpenAPI still exposes /datasets."
}

$jobProps = $trainingSpec.components.schemas.TrainingJobCreate.properties.PSObject.Properties.Name
if ($jobProps -contains "dataset_id") {
    throw "TrainingJobCreate still exposes dataset_id."
}

Write-Host "Testing game ingest/list/get/delete..."
$game = @{
    id = "SMOKE_GAME_001"
    data = @{
        matchId = "SMOKE_GAME_001"
        gameVersion = "14.10.1"
        gameType = "RANKED"
        gameDuration = 1800
        platformId = "EUW1"
    }
}
Invoke-Json POST "/api/v1/training/games" $game | Out-Null
Invoke-Json GET "/api/v1/training/games/SMOKE_GAME_001" | Out-Null
Invoke-Json GET "/api/v1/training/games?limit=5" | Out-Null
Invoke-RestMethod -Method DELETE -Uri "$BaseUrl/api/v1/training/games/SMOKE_GAME_001" | Out-Null

if ($StartTrainingJob) {
    Write-Host "Testing training job creation..."
    $job = Invoke-Json POST "/api/v1/training/jobs" @{
        concern = "draft"
        algorithm = "auto"
        sample = 0.01
    }
    if (-not $job.id) {
        throw "Training job response did not include an id."
    }
    if (($job.PSObject.Properties.Name) -contains "dataset_id") {
        throw "Training job response still exposes dataset_id."
    }
}

if ($ImportLeagueDb) {
    Write-Host "Testing LEAGUE_DB import..."
    Invoke-Json POST "/api/v1/training/games/import/league" @{
        limit = 1
        offset = 0
    } | Out-Null
}

Write-Host "Python services smoke test passed."
