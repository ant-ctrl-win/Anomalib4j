param(
    [Parameter(Mandatory=$true)][int]$RootPid,
    [Parameter(Mandatory=$true)][string]$OutputPath,
    [int]$Seconds = 5,
    [int]$IntervalMs = 100,
    [string]$StopFile,
    [string]$ReadyFile
)
$ErrorActionPreference = 'Stop'
$culture = [System.Globalization.CultureInfo]::InvariantCulture
if ($Seconds -lt 1 -or $IntervalMs -lt 10) { throw 'Invalid sampling window' }
if (Test-Path -LiteralPath $OutputPath) { throw 'Use a new output path' }
$timer = [Diagnostics.Stopwatch]::StartNew()
$rows = [Collections.Generic.List[object]]::new()
$sampleId = 0

function Read-Counter([scriptblock]$Getter) {
    try {
        $value = & $Getter
        if ($null -eq $value) { return $null }
        return [int64]$value
    } catch {
        return $null
    }
}

try {
    do {
        $sampleId++
        $inventory = @()
        $cimReason = ''
        try {
            $inventory = @(Get-CimInstance Win32_Process -ErrorAction Stop)
        } catch {
            $cimReason = 'cim_unavailable: ' + $_.Exception.Message
        }
        $selected = [Collections.Generic.HashSet[int]]::new()
        [void]$selected.Add($RootPid)
        if ($inventory.Count -gt 0) {
            do {
                $before = $selected.Count
                foreach ($item in $inventory) {
                    if ($selected.Contains([int]$item.ParentProcessId)) { [void]$selected.Add([int]$item.ProcessId) }
                }
            } while ($selected.Count -gt $before)
        }
        foreach ($processId in $selected) {
            $timestamp = [DateTime]::UtcNow.ToString('o')
            $elapsed = [string]::Format($culture, '{0:F4}', $timer.Elapsed.TotalMilliseconds)
            $process = $null
            try { $process = Get-Process -Id $processId -ErrorAction SilentlyContinue } catch { $process = $null }
            $reasons = [Collections.Generic.List[string]]::new()
            $startTime = ''
            if ($null -eq $process) {
                $reasons.Add('process_not_found')
                if ($processId -ne $RootPid) { $reasons.Add('process_terminated_between_samples') }
                $workingSet = $null
                $privateBytes = $null
                $peak = $null
            } else {
                try { $startTime = $process.StartTime.ToUniversalTime().ToString('o') } catch { $reasons.Add('start_time_unavailable') }
                $workingSet = Read-Counter { $process.WorkingSet64 }
                if ($null -eq $workingSet) { $reasons.Add('working_set_unavailable') }
                $privateBytes = Read-Counter { $process.PrivateMemorySize64 }
                if ($null -eq $privateBytes) { $reasons.Add('private_bytes_unavailable') }
                $peak = Read-Counter { $process.PeakWorkingSet64 }
                if ($null -eq $peak) { $reasons.Add('lifetime_peak_unavailable') }
            }
            if ($cimReason) { $reasons.Insert(0, $cimReason) }
            $present = 0
            if ($null -ne $workingSet) { $present++ }
            if ($null -ne $privateBytes) { $present++ }
            if ($null -ne $peak) { $present++ }
            $status = if ($present -eq 3) { 'ok' } elseif ($present -eq 0) { 'missing' } else { 'partial' }
            $rows.Add([pscustomobject]@{
                timestamp_utc = $timestamp
                sample_id = $sampleId
                elapsed_ms = $elapsed
                pid = $processId
                start_time_utc = $startTime
                root_pid = $RootPid
                working_set_bytes = $workingSet
                private_bytes = $privateBytes
                lifetime_peak_working_set_bytes = $peak
                status = $status
                reason = ($reasons -join '; ')
            })
        }
        if ($ReadyFile -and !(Test-Path -LiteralPath $ReadyFile)) { New-Item -ItemType File -Path $ReadyFile | Out-Null }
        Start-Sleep -Milliseconds $IntervalMs
    } while (($timer.Elapsed.TotalSeconds -lt $Seconds) -and !($StopFile -and (Test-Path -LiteralPath $StopFile)))
} finally {
    $rows | Export-Csv -NoTypeInformation -Encoding UTF8 -LiteralPath $OutputPath
}
Write-Output "Recorded $($rows.Count) process samples in $OutputPath"
