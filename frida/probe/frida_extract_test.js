'use strict';

function log(tag, msg) { console.log('[' + tag + '] ' + msg); }

Java.perform(function () {
  log('HOOK', 'Field+method extraction test');

  var h8 = Java.use('com.tencent.mm.storage.h8');
  h8.U8.implementation = function (msg) {
    var result = this.U8(msg);
    log('h8.U8', 'TRIGGERED result=' + result);

    // Try calling getter methods via reflection
    var clazz = msg.getClass();
    var methods = ['P0', 'j', 'getType', 'getMsgId', 'getCreateTime', 'getTalker', 'getContent',
                   'L0', 'Q1', 'R1', 'S0', 'T1', 'U0', 'U1', 'V1', 'W1', 'X0', 'X1',
                   'c2', 'd2', 'e2', 'o0', 's0', 'w0', 'z0'];

    for (var i = 0; i < methods.length; i++) {
      try {
        var m = clazz.getMethod(methods[i]);
        var val = m.invoke(msg);
        if (val !== null) {
          var s = String(val);
          if (s.length > 0 && s.length < 500) {
            log('  ' + methods[i], s.substring(0, 150));
          }
        }
      } catch (e) {
        // method not found, skip
      }
    }

    // Also check all fields including parent classes
    var currentClass = clazz;
    var depth = 0;
    while (currentClass !== null && depth < 4) {
      var fields = currentClass.getDeclaredFields();
      for (var i = 0; i < fields.length; i++) {
        var f = fields[i];
        f.setAccessible(true);
        try {
          var val = f.get(msg);
          if (val !== null) {
            var typeName = f.getType().getName();
            if (typeName === 'java.lang.String') {
              var s = String(val);
              if (s.length > 0 && s.length < 500) {
                log('  field[' + currentClass.getSimpleName() + '].' + f.getName(), s.substring(0, 100));
              }
            }
          }
        } catch (e2) {}
      }
      currentClass = currentClass.getSuperclass();
      depth++;
    }

    return result;
  };
  log('OK', 'Hooked h8.U8 with full extraction');

  log('DONE', 'Send a message now!');
});
