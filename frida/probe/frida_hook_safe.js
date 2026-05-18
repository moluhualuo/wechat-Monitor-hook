'use strict';

function log(tag, msg) { console.log('[' + tag + '] ' + msg); }

Java.perform(function () {
  log('HOOK', 'Safe hook verification (no getter hooks)');

  // Only hook storage/dispatch methods, read fields directly instead of calling methods

  // 1. Hook h8.U8(f8) -> long
  try {
    var h8 = Java.use('com.tencent.mm.storage.h8');
    h8.U8.implementation = function (msg) {
      var result = this.U8(msg);
      log('h8.U8', 'TRIGGERED! result=' + result);
      try {
        // Read fields directly to avoid triggering method hooks
        var fields = msg.getClass().getDeclaredFields();
        var info = [];
        for (var i = 0; i < fields.length && info.length < 8; i++) {
          var f = fields[i];
          f.setAccessible(true);
          var val = f.get(msg);
          if (val !== null && typeof val === 'object' && val.getClass && val.getClass().getName() === 'java.lang.String') {
            var s = String(val);
            if (s.length > 0 && s.length < 500) {
              info.push(f.getName() + '=' + s.substring(0, 80));
            }
          }
        }
        log('h8.U8', '  fields: ' + info.join(' | '));
      } catch (e) {
        log('h8.U8', '  read err: ' + e.message);
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
        var fields = msg.getClass().getDeclaredFields();
        var info = [];
        for (var i = 0; i < fields.length && info.length < 8; i++) {
          var f = fields[i];
          f.setAccessible(true);
          var val = f.get(msg);
          if (val !== null && typeof val === 'object' && val.getClass && val.getClass().getName() === 'java.lang.String') {
            var s = String(val);
            if (s.length > 0 && s.length < 500) {
              info.push(f.getName() + '=' + s.substring(0, 80));
            }
          }
        }
        log('t9.n', '  fields: ' + info.join(' | '));
      } catch (e) {
        log('t9.n', '  read err: ' + e.message);
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
      log('wc.j', 'TRIGGERED! p0=' + p0arg.getClass().getName());
      return this.j(p0arg);
    };
    log('OK', 'Hooked xv0.wc.j');
  } catch (e) {
    log('ERR', 'xv0.wc.j: ' + e.message);
  }

  // 5. Hook px0.r1.a(String) -> q1
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

  // 6. Hook px0.q1.b() -> void
  try {
    var q1 = Java.use('px0.q1');
    q1.b.implementation = function () {
      log('q1.b', 'TRIGGERED! (send)');
      return this.b();
    };
    log('OK', 'Hooked px0.q1.b');
  } catch (e) {
    log('ERR', 'px0.q1.b: ' + e.message);
  }

  // 7. Hook h8.Z8(f8) -> long (another insert variant)
  try {
    var h8 = Java.use('com.tencent.mm.storage.h8');
    h8.Z8.implementation = function (msg) {
      var result = this.Z8(msg);
      log('h8.Z8', 'TRIGGERED! result=' + result);
      return result;
    };
    log('OK', 'Hooked h8.Z8');
  } catch (e) {
    log('ERR', 'h8.Z8: ' + e.message);
  }

  // 8. Hook xv0.t9.e(f8, boolean) -> void
  try {
    var t9 = Java.use('xv0.t9');
    t9.e.implementation = function (msg, flag) {
      log('t9.e', 'TRIGGERED! flag=' + flag);
      return this.e(msg, flag);
    };
    log('OK', 'Hooked xv0.t9.e');
  } catch (e) {
    log('ERR', 'xv0.t9.e: ' + e.message);
  }

  // 9. Hook xv0.t9.C(f8) -> void
  try {
    var t9 = Java.use('xv0.t9');
    t9.C.implementation = function (msg) {
      log('t9.C', 'TRIGGERED!');
      return this.C(msg);
    };
    log('OK', 'Hooked xv0.t9.C');
  } catch (e) {
    log('ERR', 'xv0.t9.C: ' + e.message);
  }

  log('DONE', 'Safe hooks installed — send a message now!');
});
