$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$previousUsername = $env:DB_USERNAME
$previousPassword = $env:DB_PASSWORD
try {
    if (-not $env:DB_USERNAME) { $env:DB_USERNAME = Read-Host 'MySQL 계정 이름' }
    if (-not $env:DB_PASSWORD) {
        $securePassword = Read-Host 'MySQL 비밀번호' -AsSecureString
        $credential = New-Object System.Management.Automation.PSCredential($env:DB_USERNAME, $securePassword)
        $env:DB_PASSWORD = $credential.GetNetworkCredential().Password
    }
    & "$PSScriptRoot\gradlew.bat" bootRun
    if ($LASTEXITCODE -ne 0) { throw '실행에 실패했습니다. Java 17과 DB 연결 설정을 확인해 주세요.' }
} finally {
    $env:DB_USERNAME = $previousUsername
    $env:DB_PASSWORD = $previousPassword
}
