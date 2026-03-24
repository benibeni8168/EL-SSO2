$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = Get-ChildItem -Path (Join-Path $root ".tools") -Directory -Filter "jdk-*" | Select-Object -First 1

if (-not $jdk) {
    throw "No local JDK found under .tools. Download and extract a JDK 21 first."
}

$env:JAVA_HOME = $jdk.FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:MAVEN_USER_HOME = Join-Path $root ".m2-wrapper"
$mavenRepo = Join-Path $root ".m2\repository"

New-Item -ItemType Directory -Force $env:MAVEN_USER_HOME | Out-Null
New-Item -ItemType Directory -Force $mavenRepo | Out-Null

Push-Location (Join-Path $root "quarkus")
try {
    & "..\mvnw.cmd" `
        "-f" "server/pom.xml" `
        "compile" `
        "quarkus:dev" `
        "-Dkc.config.built=true" `
        "-Dkc.home.dir=../.kc" `
        "-Dmaven.repo.local=$mavenRepo" `
        '-Dquarkus.args=start-dev --bootstrap-admin-username=admin --bootstrap-admin-password=admin'
} finally {
    Pop-Location
}
