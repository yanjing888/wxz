Option Explicit

Dim fso, sh, root, scriptDir, batPath, cmdLine

Set fso = CreateObject("Scripting.FileSystemObject")
Set sh = CreateObject("WScript.Shell")

scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
root = fso.GetParentFolderName(scriptDir)
batPath = scriptDir & "\run-frontend.bat"

If Not fso.FileExists(batPath) Then
  MsgBox "未找到: " & batPath, vbCritical, "物小智"
  WScript.Quit 1
End If

cmdLine = "cmd.exe /k cd /d """ & root & """ && call """ & batPath & """"
sh.Run cmdLine, 1, False
