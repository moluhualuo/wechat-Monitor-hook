'use strict';

function log(tag, msg) { console.log('[' + tag + '] ' + msg); }

Java.perform(function () {
  log('HOOK', 'Starting live hook verification');

  // 1. Hook h8.U8(f8) -> long
  try {
    var h8 = Java.use('com.tencent.mm.storage.h8');
    h8.U8.implementation = function (msg) {
      var result = this.U8(msg);
      log('h8.U8', 'TRIGGERED! result=' + result);
      try {
        var talker = msg.P0 ? msg.P0() : 'N/A';
        var content = msg.j ? msg.j() : 'N/A';
        var type = msg.getType ? msg.getType() : -1;
        log('h8.U8', '  talker=' + talker + ' type=' + type + ' content=' + String(content).substring(0, 100));
      } catch (e) {
        log('h8.U8', '  extract err: ' + e.message);
      }
      return result;
    };
    log('OK', 'Hooked h8.U8');
  } catch (e) {
    log('ERR', 'h8.U8: ' + e.message);
  }

  // 2. Hook h8.W8(f8, boolean) -> long
  try {
    var h8 = Java.use('com.tencent.mm.storage.h8');
    h8.W8.implementation = function (msg, flag) {
      var result = this.W8(msg, flag);
      log('h8.W8', 'TRIGGERED! flag=' + flag + ' result=' + result);
      try {
        var talker = msg.P0 ? msg.P0() : 'N/A';
        var content = msg.j ? msg.j() : 'N/A';
        log('h8.W8', '  talker=' + talker + ' content=' + String(content).substring(0, 100));
      } catch (e) {
        log('h8.W8', '  extract err: ' + e.message);
      }
      return result;
    };
    log('OK', 'Hooked h8.W8');
  } catch (e) {
    log('ERR', 'h8.W8: ' + e.message);
  }

  // 3. Hook xv0.t9.n(f8, p0) -> void
  try {
    var t9 = Java.use('xv0.t9');
    t9.n.implementation = function (msg, p0arg) {
      log('t9.n', 'TRIGGERED!');
      try {
        var talker = msg.P0 ? msg.P0() : 'N/A';
        var content = msg.j ? msg.j() : 'N/A';
        log('t9.n', '  talker=' + talker + ' content=' + String(content).substring(0, 100));
        log('t9.n', '  p0 class=' + p0arg.$className);
      } catch (e) {
        log('t9.n', '  extract err: ' + e.message);
      }
      return this.n(msg, p0arg);
    };
    log('OK', 'Hooked xv0.t9.n');
  } catch (e) {
    log('ERR', 'xv0.t9.n: ' + e.message);
  }

  // 4. Hook xv0.wc.j(p0) -> q0
  try {
    var wc = Java.use('xv0.wc');
    wc.j.implementation = function (p0arg) {
      log('wc.j', 'TRIGGERED!');
      try {
        log('wc.j', '  p0 class=' + p0arg.$className);
      } catch (e) {
        log('wc.j', '  extract err: ' + e.message);
      }
      return this.j(p0arg);
    };
    log('OK', 'Hooked xv0.wc.j');
  } catch (e) {
    log('ERR', 'xv0.wc.j: ' + e.message);
  }

  // 5. Hook f8.j() -> String (plaintext getter)
  try {
    var f8 = Java.use('com.tencent.mm.storage.f8');
    var jOverloads = f8.j.overloads;
    log('INFO', 'f8.j has ' + jOverloads.length + ' overloads');
    jOverloads.forEach(function (overload) {
      if (overload.argumentTypes.length === 0) {
        overload.implementation = function () {
          var result = this.j();
          if (result && result.length > 0 && result.length < 2000) {
            log('f8.j', 'TRIGGERED! len=' + result.length + ' content=' + String(result).substring(0, 150));
          }
          return result;
        };
        log('OK', 'Hooked f8.j() no-arg');
      }
    });
  } catch (e) {
    log('ERR', 'f8.j: ' + e.message);
  }

  // 6. Hook px0.r1.a(String) -> q1
  try {
    var r1 = Java.use('px0.r1');
    r1.a.implementation = function (str) {
      log('r1.a', 'TRIGGERED! arg=' + String(str).substring(0, 100));
      return this.a(str);
    };
    log('OK', 'Hooked px0.r1.a');
  } catch (e) {
    log('ERR', 'px0.r1.a: ' + e.message);
  }

  // 7. Hook px0.q1.b() -> void (send trigger)
  try {
    var q1 = Java.use('px0.q1');
    q1.b.implementation = function () {
      log('q1.b', 'TRIGGERED! (send message)');
      return this.b();
    };
    log('OK', 'Hooked px0.q1.b');
  } catch (e) {
    log('ERR', 'px0.q1.b: ' + e.message);
  }

  log('DONE', 'All hooks installed — send a message now!');
});
