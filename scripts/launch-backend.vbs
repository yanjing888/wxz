' 强制弹出可见 cmd 窗口启动后端（绕过部分 bat 双击无反应问题）
Option Explicit

Dim fso, sh, root, scriptDir, batPath, cmdLine

Set fso = CreateObject("Scripting.FileSystemObject")
Set sh = CreateObject("WScript.Shell")

scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
root = fso.GetParentFolderName(scriptDir)
batPath = scriptDir & "\run-backend-jar.bat"

If Not fso.FileExists(batPath) Then
  MsgBox "未找到: " & batPath, vbCritical, "物小智"
  WScript.Quit 1
End If

On Error Resume Next
Dim logsDir
logsDir = root & "\logs"
If Not fso.FolderExists(logsDir) Then fso.CreateFolder logsDir
Dim logFile
Set logFile = fso.OpenTextFile(logsDir & "\startup.log", 8, True)
logFile.WriteLine Now & " launch-backend.vbs"
logFile.Close
On Error GoTo 0

cmdLine = "cmd.exe /k cd /d """ & root & """ && call """ & batPath & """"
sh.Run cmdLine, 1, False
