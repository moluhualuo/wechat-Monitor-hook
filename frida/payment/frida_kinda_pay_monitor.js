/*
 * libkinda_android.so 支付消息监听
 *
 * Hook 点：
 *   1. notifyHKOfflineNewXml (0x77DF34) — 香港钱包离线支付 XML 通知
 *   2. OfflineUseCase 消息分发 (0xAB0834) — 离线支付消息分发主函数
 *   3. paymsg 处理函数 (0xBE9628) — 支付消息处理
 *   4. handleShowCashierCmd (0xA70B24) — 收银台命令处理
 *
 * 用法：
 *   frida -U -f com.tencent.mm -l frida/frida_kinda_pay_monitor.js
 *   frida -U -p <PID> -l frida/frida_kinda_pay_monitor.js
 */
'use strict';

const LIB = 'libkinda_android.so';

function now() {
  return new Date().toISOString().replace('T', ' ').replace('Z', '');
}

function log(tag, msg) {
  const line = `[${now()}] [${tag}] ${msg}`;
  console.log(line);
  try { send({ type: tag, line: line }); } catch (e) {}
}

function readStdString(ptr) {
  try {
    if (ptr.isNull()) return '';
    const sso = ptr.add(8).readPointer();
    if (sso.isNull()) return '';
    return sso.readUtf8String();
  } catch (e) {
    try { return ptr.readUtf8String(); } catch (e2) { return ''; }
  }
}

function readStdStringOrPtr(ptr, offset) {
  try {
    const p = ptr.add(offset);
    return readStdString(p);
  } catch (e) { return ''; }
}

function tryReadArgString(arg) {
  try {
    if (arg.isNull()) return '<null>';
    const s = readStdString(arg);
    if (s && s.length > 0 && s.length < 4096) return s;
    return arg.toString();
  } catch (e) {
    return arg.toString();
  }
}

// ============ Hook 1: notifyHKOfflineNewXml ============
function hookNotifyHKOfflineNewXml(base) {
  const addr = base.add(0x77DF34);
  Interceptor.attach(addr, {
    onEnter(args) {
      this.arg0 = args[0];
      this.arg1 = args[1];
      log('HK-OFFLINE', 'notifyHKOfflineNewXml enter');
    },
    onLeave(retval) {
      log('HK-OFFLINE', 'notifyHKOfflineNewXml leave ret=' + retval);
    }
  });
  log('HOOK', 'notifyHKOfflineNewXml @ 0x77DF34 OK');
}

// ============ Hook 2: OfflineUseCase 消息分发 ============
function hookOfflineUseCaseDispatch(base) {
  const addr = base.add(0xAB0834);
  Interceptor.attach(addr, {
    onEnter(args) {
      this.self = args[0];
      this.arg1 = args[1];
      log('OFFLINE-DISPATCH', 'OfflineUseCase msg dispatch enter');
    },
    onLeave(retval) {
      log('OFFLINE-DISPATCH', 'OfflineUseCase msg dispatch leave');
    }
  });
  log('HOOK', 'OfflineUseCase dispatch @ 0xAB0834 OK');
}

// ============ Hook 3: paymsg 处理函数 ============
function hookPayMsgHandler(base) {
  const addr = base.add(0xBE9628);
  Interceptor.attach(addr, {
    onEnter(args) {
      log('PAYMSG', 'paymsg handler enter (publishPayLiteAppGlobalEvent)');
    },
    onLeave(retval) {
      log('PAYMSG', 'paymsg handler leave');
    }
  });
  log('HOOK', 'paymsg handler @ 0xBE9628 OK');
}

// ============ Hook 4: handleShowCashierCmd ============
function hookHandleShowCashierCmd(base) {
  const addr = base.add(0xA70B24);
  Interceptor.attach(addr, {
    onEnter(args) {
      this.self = args[0];
      this.cmdPtr = args[1];
      try {
        const cmdType = this.cmdPtr.add(32).readS32();
        log('CASHIER-CMD', 'handleShowCashierCmd enter cmdType=' + cmdType);
      } catch (e) {
        log('CASHIER-CMD', 'handleShowCashierCmd enter (cmdType read fail)');
      }
    },
    onLeave(retval) {
      log('CASHIER-CMD', 'handleShowCashierCmd leave');
    }
  });
  log('HOOK', 'handleShowCashierCmd @ 0xA70B24 OK');
}

// ============ Hook 5: notifyPayerMsgListUpdate ============
function hookNotifyPayerMsgListUpdate(base) {
  const addr1 = base.add(0x8519F4);
  Interceptor.attach(addr1, {
    onEnter(args) {
      log('PAYER-MSG', 'notifyPayerMsgListUpdate enter');
    },
    onLeave(retval) {
      log('PAYER-MSG', 'notifyPayerMsgListUpdate leave');
    }
  });
  log('HOOK', 'notifyPayerMsgListUpdate @ 0x8519F4 OK');

  const addr2 = base.add(0x833828);
  Interceptor.attach(addr2, {
    onEnter(args) {
      log('PAYER-MSG', 'notifyPayerMsgListUpdate(refresh_type) enter');
    },
    onLeave(retval) {
      log('PAYER-MSG', 'notifyPayerMsgListUpdate(refresh_type) leave');
    }
  });
  log('HOOK', 'notifyPayerMsgListUpdate(refresh_type) @ 0x833828 OK');
}

// ============ Hook 6: 收款消息回调 (Java 层) ============
function hookCollectPayerCallback() {
  try {
    const Cls = Java.use('com.tencent.kinda.gen.VoidKCollectPayerMsgCallback$CppProxy');
    Cls.call.implementation = function (msg) {
      log('COLLECT-PAYER', 'KCollectPayerMsgCallback.call msg=' + msg);
      return this.call(msg);
    };
    log('HOOK', 'KCollectPayerMsgCallback.call OK');
  } catch (e) {
    log('HOOK-FAIL', 'KCollectPayerMsgCallback: ' + e.message);
  }
}

// ============ Hook 7: 收银台消息处理 ============
function hookHandleShowCashierWithMsgInfo(base) {
  const addr = base.add(0xA77EA0);
  Interceptor.attach(addr, {
    onEnter(args) {
      log('CASHIER-MSG', 'handleShowCashierWithCashierMsgInfo enter');
    },
    onLeave(retval) {
      log('CASHIER-MSG', 'handleShowCashierWithCashierMsgInfo leave');
    }
  });
  log('HOOK', 'handleShowCashierWithCashierMsgInfo @ 0xA77EA0 OK');
}

// ============ 字符串监控：在关键字符串处监控读取 ============
function hookStringAccess(base) {
  const strings = [
    { addr: 0xC4D7B, name: 'PayMsgInfo' },
    { addr: 0xC6EEB, name: 'paymsg_type' },
    { addr: 0xFA49A, name: 'PayMsgType' },
    { addr: 0xFB7E2, name: 'ackOfflineMsg' },
    { addr: 0xF83A3, name: 'notifyHKOfflineNewXml paymsg' },
  ];

  strings.forEach(function (s) {
    try {
      const strAddr = base.add(s.addr);
      Memory.accessMonitor.watch(strAddr, s.name.length, {
        onAccess: function (details) {
          log('STR-ACCESS', `"${s.name}" accessed from ${details.from}`);
        }
      });
    } catch (e) {
      // Memory.accessMonitor 可能不可用，忽略
    }
  });
}

// ============ 主入口 ============
function main() {
  log('INIT', 'libkinda payment monitor starting...');

  const base = Module.findBaseAddress(LIB);
  if (!base) {
    log('ERROR', LIB + ' not loaded! Waiting for module load...');
    const interval = setInterval(function () {
      const b = Module.findBaseAddress(LIB);
      if (b) {
        clearInterval(interval);
        log('INIT', LIB + ' loaded at ' + b);
        installHooks(b);
      }
    }, 1000);
    return;
  }

  log('INIT', LIB + ' base=' + base);
  installHooks(base);
}

function installHooks(base) {
  log('INIT', 'Installing native hooks...');

  // Native hooks
  try { hookNotifyHKOfflineNewXml(base); } catch (e) { log('HOOK-FAIL', 'notifyHKOfflineNewXml: ' + e.message); }
  try { hookOfflineUseCaseDispatch(base); } catch (e) { log('HOOK-FAIL', 'OfflineUseCase: ' + e.message); }
  try { hookPayMsgHandler(base); } catch (e) { log('HOOK-FAIL', 'paymsg handler: ' + e.message); }
  try { hookHandleShowCashierCmd(base); } catch (e) { log('HOOK-FAIL', 'handleShowCashierCmd: ' + e.message); }
  try { hookNotifyPayerMsgListUpdate(base); } catch (e) { log('HOOK-FAIL', 'notifyPayerMsgListUpdate: ' + e.message); }
  try { hookHandleShowCashierWithMsgInfo(base); } catch (e) { log('HOOK-FAIL', 'handleShowCashierWithMsgInfo: ' + e.message); }

  // Java hooks
  Java.perform(function () {
    log('INIT', 'Java VM ready');
    hookCollectPayerCallback();

    // 监听 KQRCodeCollectionService 初始化
    try {
      const Svc = Java.use('com.tencent.kinda.gen.KQRCodeCollectionService');
      log('INFO', 'KQRCodeCollectionService found');
    } catch (e) {
      log('INFO', 'KQRCodeCollectionService not found (normal if not loaded yet)');
    }

    // 监听 TenpayCgiCallback
    try {
      const Tcb = Java.use('com.tencent.kinda.gen.TenpayCgiCallback$CppProxy');
      Tcb.native_onSuccess.implementation = function (resp) {
        log('TENPAY-CGI', 'TenpayCgiCallback.onSuccess');
        return this.native_onSuccess(resp);
      };
      Tcb.native_onError.implementation = function (errCode, errMsg) {
        log('TENPAY-CGI', 'TenpayCgiCallback.onError code=' + errCode + ' msg=' + errMsg);
        return this.native_onError(errCode, errMsg);
      };
      log('HOOK', 'TenpayCgiCallback OK');
    } catch (e) {
      log('HOOK-FAIL', 'TenpayCgiCallback: ' + e.message);
    }
  });

  log('READY', 'All hooks installed. Waiting for payment events...');
  log('READY', 'Hook points:');
  log('READY', '  [Native] notifyHKOfflineNewXml @ 0x77DF34');
  log('READY', '  [Native] OfflineUseCase dispatch @ 0xAB0834');
  log('READY', '  [Native] paymsg handler @ 0xBE9628');
  log('READY', '  [Native] handleShowCashierCmd @ 0xA70B24');
  log('READY', '  [Native] notifyPayerMsgListUpdate @ 0x8519F4 / 0x833828');
  log('READY', '  [Native] handleShowCashierWithMsgInfo @ 0xA77EA0');
  log('READY', '  [Java]   KCollectPayerMsgCallback.call');
  log('READY', '  [Java]   TenpayCgiCallback.onSuccess / onError');
}

main();
