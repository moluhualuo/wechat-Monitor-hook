/*
 * WeChat 8.0.68 friend message send chain monitor.
 * Hooks Java-level send request construction and NetSceneSendMsg lifecycle.
 */
'use strict';

function now() {
  const d = new Date();
  return d.toISOString().replace('T', ' ').replace('Z', '');
}

function log(tag, msg) {
  const line = `[${now()}] [${tag}] ${msg}`;
  console.log(line);
  try { send({ type: tag, line: line }); } catch (e) {}
}

function toStr(v) {
  if (v === null || v === undefined) return '';
  try {
    if (typeof v === 'string') return v;
    return v.toString();
  } catch (e) {
    return '<toString-error>';
  }
}

function safeCall(obj, methodName) {
  try {
    if (!obj || !obj[methodName]) return '';
    return toStr(obj[methodName]());
  } catch (e) {
    return '';
  }
}

function getField(obj, name) {
  try {
    const cls = obj.getClass();
    const f = cls.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(obj);
  } catch (e) {
    return null;
  }
}

function dumpQ1(q1) {
  if (!q1) return '<null q1>';
  const talker = toStr(getField(q1, 'b'));
  const content = toStr(getField(q1, 'd'));
  const type = toStr(getField(q1, 'e'));
  const flag = toStr(getField(q1, 'f'));
  const localId = toStr(getField(q1, 'g'));
  const mode = toStr(getField(q1, 'i'));
  const extraText = toStr(getField(q1, 'n'));
  return `talker=${talker} type=${type} flag=${flag} localId=${localId} mode=${mode} extra=${extraText} content=${content}`;
}

function dumpContentValues(cv) {
  if (!cv) return '{}';
  try {
    const keys = cv.keySet();
    const iter = keys.iterator();
    const parts = [];
    while (iter.hasNext()) {
      const k = toStr(iter.next());
      parts.push(k + '=' + toStr(cv.get(k)));
    }
    return '{' + parts.join(', ') + '}';
  } catch (e) {
    return toStr(cv);
  }
}

function javaStack() {
  try {
    const Log = Java.use('android.util.Log');
    const Throwable = Java.use('java.lang.Throwable');
    return Log.getStackTraceString(Throwable.$new()).toString().split('\n').filter(function (line) {
      return line.indexOf('com.tencent.mm') >= 0 ||
        line.indexOf('px0.') >= 0 ||
        line.indexOf('fo1.') >= 0 ||
        line.indexOf('wcdb') >= 0;
    }).slice(0, 35).join(' | ');
  } catch (e) {
    return '<stack-error ' + e.message + '>';
  }
}

function hookAllOverloads(Cls, methodName, tag, formatter) {
  try {
    Cls[methodName].overloads.forEach(function (ov) {
      ov.implementation = function () {
        const ret = ov.apply(this, arguments);
        try { log(tag, formatter.call(this, arguments, ret)); } catch (e) { log(tag, 'format-error=' + e.message); }
        return ret;
      };
    });
    log('HOOK', `${Cls.$className}.${methodName}`);
  } catch (e) {
    log('HOOK-FAIL', `${methodName}: ${e.message}`);
  }
}

Java.perform(function () {
  log('INIT', 'Java VM ready');

  try {
    const R1 = Java.use('px0.r1');
    hookAllOverloads(R1, 'a', 'R1.a', function (args, ret) {
      return `talker=${toStr(args[0])} -> ${dumpQ1(ret)}`;
    });
  } catch (e) {
    log('HOOK-FAIL', 'px0.r1: ' + e.message);
  }

  try {
    const Q1 = Java.use('px0.q1');
    hookAllOverloads(Q1, 'g', 'Q1.g(talker)', function (args, ret) {
      return `arg=${toStr(args[0])} state=${dumpQ1(this)}`;
    });
    hookAllOverloads(Q1, 'e', 'Q1.e(content)', function (args, ret) {
      return `arg=${toStr(args[0])} state=${dumpQ1(this)}`;
    });
    hookAllOverloads(Q1, 'h', 'Q1.h(type)', function (args, ret) {
      return `arg=${toStr(args[0])} state=${dumpQ1(this)}`;
    });
    hookAllOverloads(Q1, 'a', 'Q1.a(exec)', function (args, ret) {
      return `state=${dumpQ1(this)} ret=${toStr(ret)}`;
    });
    hookAllOverloads(Q1, 'b', 'Q1.b(exec)', function () {
      return `state=${dumpQ1(this)}`;
    });
  } catch (e) {
    log('HOOK-FAIL', 'px0.q1: ' + e.message);
  }

  try {
    const G = Java.use('fo1.g');
    hookAllOverloads(G, 'j', 'FO1.g.j', function (args, ret) {
      return `q1=${dumpQ1(args[0])} ret=${toStr(ret)}`;
    });
  } catch (e) {
    log('HOOK-FAIL', 'fo1.g: ' + e.message);
  }

  try {
    const R0 = Java.use('px0.r0');
    R0.$init.overloads.forEach(function (ov) {
      ov.implementation = function () {
        const ret = ov.apply(this, arguments);
        const args = Array.prototype.map.call(arguments, toStr).join(', ');
        log('R0.<init>', `args=[${args}] scene=${toStr(this)}`);
        return ret;
      };
    });
    log('HOOK', 'px0.r0.$init');

    hookAllOverloads(R0, 'doScene', 'R0.doScene', function (args, ret) {
      return `args=${Array.prototype.map.call(args, toStr).join(', ')} ret=${toStr(ret)}`;
    });
    hookAllOverloads(R0, 'getType', 'R0.getType', function (args, ret) {
      return `ret=${toStr(ret)}`;
    });
    hookAllOverloads(R0, 'onGYNetEnd', 'R0.onGYNetEnd', function (args) {
      return `args=${Array.prototype.map.call(args, toStr).join(', ')}`;
    });
  } catch (e) {
    log('HOOK-FAIL', 'px0.r0: ' + e.message);
  }

  try {
    const Sdb = Java.use('com.tencent.wcdb.database.SQLiteDatabase');
    ['insert', 'insertWithOnConflict', 'replace', 'update'].forEach(function (methodName) {
      try {
        Sdb[methodName].overloads.forEach(function (ov) {
          ov.implementation = function () {
            const table = toStr(arguments[0]);
            const ret = ov.apply(this, arguments);
            if (table === 'message') {
              const cvIndex = methodName === 'update' ? 1 : 2;
              log('WCDB.' + methodName, `table=${table} cv=${dumpContentValues(arguments[cvIndex])} ret=${toStr(ret)} stack=${javaStack()}`);
            }
            return ret;
          };
        });
        log('HOOK', 'SQLiteDatabase.' + methodName);
      } catch (e) {
        log('HOOK-FAIL', 'SQLiteDatabase.' + methodName + ': ' + e.message);
      }
    });
  } catch (e) {
    log('HOOK-FAIL', 'SQLiteDatabase: ' + e.message);
  }

  log('READY', 'Send a friend text message in WeChat to trace the send chain');
});
