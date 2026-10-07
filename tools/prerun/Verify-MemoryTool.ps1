param([string]$RunId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff'))
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output = Join-Path $root 'docs/benchmark/benchmark-prerun'
$python = Join-Path $root 'target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe'
$samplePath = Join-Path $output "memory-python-$RunId.csv"
$process = Start-Process -FilePath $python -ArgumentList '-c','"import time; a=bytearray(32000000); time.sleep(4)"' -WindowStyle Hidden -PassThru
& (Join-Path $PSScriptRoot 'Measure-ProcessMemory.ps1') -RootPid $process.Id -OutputPath $samplePath -Seconds 2 -IntervalMs 100
$process.WaitForExit()
if ($process.ExitCode -ne 0) { throw 'Python process probe failed' }
$rows = @(Import-Csv -LiteralPath $samplePath)
if ($rows.Count -eq 0 -or [long]$rows[-1].private_bytes -lt 32000000) { throw 'Memory allocation probe not observed' }
Write-Output 'Python allocation observed via OS process counters; no model performance measured.'
