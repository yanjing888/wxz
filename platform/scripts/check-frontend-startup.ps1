$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start();$testPort=$listener.LocalEndpoint.Port;$listener.Stop()
$logRoot=Join-Path $projectRoot 'logs'
$entry=Join-Path $projectRoot 'frontend\scripts\run-vite.mjs'
$starter=Join-Path $PSScriptRoot 'start-frontend.ps1'
$stopper=Join-Path $PSScriptRoot 'stop-frontend.ps1'
$stdout=Join-Path $logRoot ('frontend-console-test-'+$testPort+'.out.log')
$stderr=Join-Path $logRoot ('frontend-console-test-'+$testPort+'.err.log')
$launcher=$null
function Get-TestOwner {
    Get-NetTCPConnection -LocalPort $testPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
}
try {
    $launcher=Start-Process powershell.exe -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$starter+'"'),'-Port',$testPort,'-BindAddress','127.0.0.1') -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $null=$launcher.Handle
    $ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++) {
        if($launcher.HasExited){throw ('Foreground launcher exited early; see '+$stderr)}
        try{if((Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:'+$testPort+'/') -TimeoutSec 2).StatusCode -eq 200){$ready=$true;break}}catch{}
        Start-Sleep -Milliseconds 300
    }
    if(-not $ready){throw 'Foreground server startup timed out'}
    $owner=Get-TestOwner
    $node=Get-CimInstance Win32_Process -Filter ('ProcessId='+$owner.OwningProcess)
    if(-not $node.CommandLine.Contains($entry)){throw 'Unexpected port owner'}
    if($node.ParentProcessId -ne $launcher.Id){throw 'Vite must be directly owned by the foreground launcher'}
    if([int](& node.exe -p "process.versions.node.split('.')[0]") -ge 22 -and -not $node.CommandLine.Contains('--no-maglev')){throw 'Windows runtime mitigation is missing'}
    # Simulate the observed native failure on this isolated test port only.
    Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class FrontendCrashTest { [DllImport("kernel32.dll", SetLastError=true)] public static extern bool TerminateProcess(IntPtr handle, uint code); }'
    $testNode=Get-Process -Id $owner.OwningProcess
    if(-not [FrontendCrashTest]::TerminateProcess($testNode.Handle,[uint32]3221226505)){throw 'Could not simulate native crash'}
    $recovered=$false
    for($attempt=0;$attempt -lt 40;$attempt++) {
        Start-Sleep -Milliseconds 300
        $replacement=Get-TestOwner
        if($replacement -and $replacement.OwningProcess -ne $owner.OwningProcess) {
            try{if((Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:'+$testPort+'/') -TimeoutSec 2).StatusCode -eq 200){$owner=$replacement;$recovered=$true;break}}catch{}
        }
    }
    if(-not $recovered){throw 'Native crash did not recover automatically'}
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $starter -Port $testPort -BindAddress 127.0.0.1
    if($LASTEXITCODE -ne 0 -or (Get-TestOwner).OwningProcess -ne $owner.OwningProcess){throw 'Repeat start replaced the server'}
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $stopper -Port $testPort
    if(-not $launcher.WaitForExit(10000)){throw 'Foreground launcher did not report child exit'}
    Start-Sleep -Seconds 2
    if(Get-TestOwner){throw 'Server unexpectedly restarted after stop'}
    Write-Output 'Passed: foreground ownership, runtime mitigation, native crash recovery, real HTTP response, duplicate start reuses Vite, explicit stop exits without restarting.'
} finally {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $stopper -Port $testPort
    if($launcher -and -not $launcher.HasExited){$launcher.Kill()}
}
