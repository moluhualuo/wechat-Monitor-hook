# Hook 不生效快速诊断脚本
# 使用方法: .\diagnose.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "微信Hook诊断工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查adb连接
Write-Host "[1/8] 检查ADB连接..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$"
if ($devices.Count -eq 0) {
    Write-Host "❌ 未检测到设备，请确保手机已连接并开启USB调试" -ForegroundColor Red
    exit 1
}
Write-Host "✓ 设备已连接" -ForegroundColor Green
Write-Host ""

# 检查模块是否安装
Write-Host "[2/8] 检查模块安装状态..." -ForegroundColor Yellow
$moduleInstalled = adb shell pm list packages | Select-String "com.wechat.monitor"
if ($moduleInstalled) {
    Write-Host "✓ 模块已安装: $moduleInstalled" -ForegroundColor Green
} else {
    Write-Host "❌ 模块未安装" -ForegroundColor Red
    Write-Host "请运行: adb install -r .\app\build\outputs\apk\release\app-release.apk" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 检查微信是否安装
Write-Host "[3/8] 检查微信安装状态..." -ForegroundColor Yellow
$wechatInstalled = adb shell pm list packages | Select-String "com.tencent.mm"
if ($wechatInstalled) {
    Write-Host "✓ 微信已安装: $wechatInstalled" -ForegroundColor Green

    # 获取微信版本
    $wechatVersion = adb shell dumpsys package com.tencent.mm | Select-String "versionName"
    if ($wechatVersion) {
        Write-Host "  版本: $wechatVersion" -ForegroundColor Cyan
    }
} else {
    Write-Host "❌ 微信未安装" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 检查LSPosed
Write-Host "[4/8] 检查LSPosed框架..." -ForegroundColor Yellow
$lsposed = adb shell pm list packages | Select-String "lsposed"
if ($lsposed) {
    Write-Host "✓ LSPosed已安装: $lsposed" -ForegroundColor Green
} else {
    Write-Host "❌ LSPosed未安装" -ForegroundColor Red
    Write-Host "请先安装LSPosed框架" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# 强制停止微信
Write-Host "[5/8] 强制停止微信..." -ForegroundColor Yellow
adb shell am force-stop com.tencent.mm
Start-Sleep -Seconds 2
Write-Host "✓ 微信已停止" -ForegroundColor Green
Write-Host ""

# 清空日志
Write-Host "[6/8] 清空日志缓冲区..." -ForegroundColor Yellow
adb logcat -c
Write-Host "✓ 日志已清空" -ForegroundColor Green
Write-Host ""

# 启动微信
Write-Host "[7/8] 启动微信..." -ForegroundColor Yellow
adb shell am start -n com.tencent.mm/.ui.LauncherUI
Start-Sleep -Seconds 3
Write-Host "✓ 微信已启动" -ForegroundColor Green
Write-Host ""

# 捕获日志
Write-Host "[8/8] 捕获Hook日志（10秒）..." -ForegroundColor Yellow
Write-Host "正在监听日志，请在微信中进行操作..." -ForegroundColor Cyan
Write-Host ""

$logFile = ".\hook_diagnostic_log_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
$process = Start-Process -FilePath "adb" -ArgumentList "logcat" -RedirectStandardOutput $logFile -NoNewWindow -PassThru

Start-Sleep -Seconds 10
Stop-Process -Id $process.Id -Force

Write-Host "✓ 日志已保存到: $logFile" -ForegroundColor Green
Write-Host ""

# 分析日志
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "日志分析结果" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$logContent = Get-Content $logFile -Raw

# 检查模块加载
$moduleLoaded = $logContent | Select-String "WeChatMonitor.*handleLoadPackage"
if ($moduleLoaded) {
    Write-Host "✓ 模块已加载" -ForegroundColor Green
    $moduleLoaded | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
} else {
    Write-Host "❌ 模块未加载" -ForegroundColor Red
    Write-Host "可能原因：" -ForegroundColor Yellow
    Write-Host "  1. LSPosed中未启用模块" -ForegroundColor Yellow
    Write-Host "  2. 作用域未勾选com.tencent.mm" -ForegroundColor Yellow
    Write-Host "  3. LSPosed框架未正常工作" -ForegroundColor Yellow
}
Write-Host ""

# 检查进程
$processInfo = $logContent | Select-String "WeChatMonitor.*process="
if ($processInfo) {
    Write-Host "检测到的进程：" -ForegroundColor Cyan
    $processInfo | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }

    $mainProcess = $processInfo | Select-String "process=com.tencent.mm[^:]"
    if ($mainProcess) {
        Write-Host "✓ 已进入主进程" -ForegroundColor Green
    } else {
        Write-Host "⚠ 未检测到主进程，只进入了子进程" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ 未检测到进程信息" -ForegroundColor Red
}
Write-Host ""

# 检查Hook安装
$hookInstalled = $logContent | Select-String "WeChatMonitor.*hooked"
if ($hookInstalled) {
    Write-Host "✓ Hook已安装" -ForegroundColor Green
    $hookCount = ($hookInstalled | Measure-Object).Count
    Write-Host "  共安装 $hookCount 个Hook点" -ForegroundColor Cyan

    # 显示关键Hook
    $keyHooks = $hookInstalled | Select-String "(xv0\.t9\.n|xv0\.wc\.j|IEvent\.e|EventPool\.d)"
    if ($keyHooks) {
        Write-Host "  关键Hook点：" -ForegroundColor Cyan
        $keyHooks | Select-Object -First 5 | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
    }
} else {
    Write-Host "❌ Hook未安装" -ForegroundColor Red
    Write-Host "可能原因：" -ForegroundColor Yellow
    Write-Host "  1. 微信版本不匹配（需要8.0.68）" -ForegroundColor Yellow
    Write-Host "  2. 类名或方法名已改变" -ForegroundColor Yellow
}
Write-Host ""

# 检查消息日志
$messageLog = $logContent | Select-String "WeChatMonitor.*\[(消息|红包|转账|支付)\]"
if ($messageLog) {
    Write-Host "✓ 检测到消息日志" -ForegroundColor Green
    $messageCount = ($messageLog | Measure-Object).Count
    Write-Host "  共捕获 $messageCount 条消息" -ForegroundColor Cyan
    Write-Host "  最近的消息：" -ForegroundColor Cyan
    $messageLog | Select-Object -Last 3 | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
} else {
    Write-Host "⚠ 未检测到消息日志" -ForegroundColor Yellow
    Write-Host "请尝试：" -ForegroundColor Yellow
    Write-Host "  1. 让其他设备给你发送消息" -ForegroundColor Yellow
    Write-Host "  2. 在微信中查看消息" -ForegroundColor Yellow
    Write-Host "  3. 检查模块设置中是否启用了监听" -ForegroundColor Yellow
}
Write-Host ""

# 检查错误
$errors = $logContent | Select-String "WeChatMonitor.*(failed|error|exception)"
if ($errors) {
    Write-Host "⚠ 检测到错误" -ForegroundColor Yellow
    $errors | Select-Object -First 5 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
}
Write-Host ""

# 总结
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "诊断总结" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if ($moduleLoaded -and $hookInstalled -and $mainProcess) {
    Write-Host "✓ Hook基础功能正常" -ForegroundColor Green
    if ($messageLog) {
        Write-Host "✓ 消息监听正常工作" -ForegroundColor Green
        Write-Host ""
        Write-Host "🎉 恭喜！Hook已成功生效！" -ForegroundColor Green
    } else {
        Write-Host "⚠ Hook已安装但未捕获到消息" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "建议操作：" -ForegroundColor Cyan
        Write-Host "1. 让其他设备给你发送测试消息" -ForegroundColor White
        Write-Host "2. 在微信中打开聊天窗口查看消息" -ForegroundColor White
        Write-Host "3. 打开模块App，确认'启用监听'已开启" -ForegroundColor White
        Write-Host "4. 再次运行此诊断脚本" -ForegroundColor White
    }
} else {
    Write-Host "❌ Hook未正常工作" -ForegroundColor Red
    Write-Host ""
    Write-Host "请检查：" -ForegroundColor Cyan
    if (-not $moduleLoaded) {
        Write-Host "1. 在LSPosed Manager中启用模块" -ForegroundColor White
        Write-Host "2. 在作用域中勾选'微信 (com.tencent.mm)'" -ForegroundColor White
    }
    if (-not $mainProcess) {
        Write-Host "3. 确保进入的是主进程而不是子进程" -ForegroundColor White
    }
    if (-not $hookInstalled) {
        Write-Host "4. 确认微信版本是8.0.68" -ForegroundColor White
        Write-Host "5. 如果版本不匹配，需要重新适配Hook点" -ForegroundColor White
    }
}
Write-Host ""

Write-Host "完整日志已保存到: $logFile" -ForegroundColor Cyan
Write-Host "如需帮助，请查看: .\docs\hook不生效诊断指南.md" -ForegroundColor Cyan
Write-Host ""
