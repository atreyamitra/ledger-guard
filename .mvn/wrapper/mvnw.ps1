$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$cache = Join-Path $env:USERPROFILE '.m2/wrapper/dists/ledger-guard-3.9.9'
$maven = Join-Path $cache 'apache-maven-3.9.9'
if (!(Test-Path "$maven/bin/mvn.cmd")) {
    $url = (Get-Content "$PSScriptRoot/maven-wrapper.properties" | Select-String '^distributionUrl=').ToString().Substring(16)
    $temp = Join-Path $cache ([guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Force $temp | Out-Null
    try {
        Invoke-WebRequest $url -OutFile "$temp/maven.zip"
        $expected = ((Invoke-WebRequest "$url.sha512").Content.Trim() -split '\s+')[0]
        if ((Get-FileHash "$temp/maven.zip" -Algorithm SHA512).Hash -ne $expected) { throw 'Maven checksum mismatch' }
        Expand-Archive "$temp/maven.zip" $temp
        Move-Item "$temp/apache-maven-3.9.9" $maven
    } finally { Remove-Item $temp -Recurse -Force }
}
& "$maven/bin/mvn.cmd" '-f' "$root/pom.xml" @args
exit $LASTEXITCODE
