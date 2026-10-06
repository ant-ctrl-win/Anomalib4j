# Compiles the comparison classes and writes target/comparison-classpath.txt.
# Read-only with respect to sources and data; only target/ is written.
# Run from the repository root: powershell -NoProfile -File tools/comparison/bootstrap.ps1
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

& mvn -B -q -Pbenchmark -DskipTests -f (Join-Path $root 'pom.xml') test-compile
if ($LASTEXITCODE -ne 0) { throw 'test-compile failed' }

& mvn -B -q -Pbenchmark -f (Join-Path $root 'pom.xml') org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/comparison-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'build-classpath failed' }

Write-Output ('Wrote ' + (Join-Path $root 'target/comparison-classpath.txt'))
