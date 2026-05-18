/*
 * Dynamic probe: find plaintext getter methods on WeChat msg objects
 * Runs on-device to discover working hook targets for the current WeChat version.
 */
'use strict';

function log(tag, msg) { console.log(`[${tag}] ${msg}`); }

function getMethodSignatures(clazz, filterRegex) {
  var methods = clazz.getDeclaredMethods();
  var result = [];
  for (var i = 0; i < methods.length; i++) {
    var m = methods[i];
    var name = m.getName();
    if (filterRegex && !filterRegex.test(name)) continue;
    var params = m.getParameterTypes();
    var paramStr = [];
    for (var j = 0; j < params.length; j++) {
      paramStr.push(params[j].getSimpleName());
    }
    result.push({
      name: name,
      returns: m.getReturnType().getSimpleName(),
      params: paramStr
    });
  }
  return result;
}

Java.perform(function () {
  log('PROBE', 'Java VM ready');

  // 1. Find all getter methods on ok.y7
  try {
    var y7 = Java.use('ok.y7');
    var allMethods = y7.class.getDeclaredMethods();
    log('ok.y7', 'total methods: ' + allMethods.length);

    // Find no-arg methods that return String
    var stringGetters = [];
    var otherGetters = [];
    for (var i = 0; i < allMethods.length; i++) {
      var m = allMethods[i];
      var name = m.getName();
      var rt = m.getReturnType().getSimpleName();
      var pc = m.getParameterTypes().length;
      if (pc === 0) {
        if (rt === 'String') {
          stringGetters.push(name);
        } else if (rt === 'int' || rt === 'long' || rt === 'boolean') {
          otherGetters.push(name + ':' + rt);
        }
      }
    }
    log('  String getters (no-arg)', stringGetters.sort().join(', '));
    log('  Other getters (no-arg)', otherGetters.sort().join(', '));
  } catch (e) {
    log('ERR', 'ok.y7: ' + e.message);
  }

  // 2. Find all getter methods on f8
  try {
    var f8 = Java.use('com.tencent.mm.storage.f8');
    var allF8 = f8.class.getDeclaredMethods();
    log('f8', 'total methods: ' + allF8.length);

    var stringGetters = [];
    var otherGetters = [];
    for (var i = 0; i < allF8.length; i++) {
      var m = allF8[i];
      var name = m.getName();
      var rt = m.getReturnType().getSimpleName();
      var pc = m.getParameterTypes().length;
      if (pc === 0) {
        if (rt === 'String') {
          stringGetters.push(name);
        } else if (rt === 'int' || rt === 'long' || rt === 'boolean') {
          otherGetters.push(name + ':' + rt);
        }
      }
    }
    log('  String getters (no-arg)', stringGetters.sort().join(', '));
    log('  Other getters (no-arg)', otherGetters.sort().join(', '));
  } catch (e) {
    log('ERR', 'f8: ' + e.message);
  }

  // 3. Actually test calling each candidate to see what returns useful data
  log('---', 'Testing msg instance — send a message now to create a fresh msg object ---');

  // Hook f8 constructor to test getters on a real instance
  try {
    var f8Class = Java.use('com.tencent.mm.storage.f8');
    var tested = false;
    f8Class.$init.overloads.forEach(function (ov) {
      if (tested) return;
      ov.implementation = function () {
        var inst = ov.apply(this, arguments);
        log('f8 instance', 'New f8 created, testing getters...');
        tested = true;

        var methods = this.getClass().getDeclaredMethods();
        for (var i = 0; i < methods.length; i++) {
          var m = methods[i];
          var name = m.getName();
          if (m.getParameterTypes().length === 0 && m.getReturnType().getSimpleName() === 'String') {
            try {
              var val = m.invoke(this);
              if (val !== null && val.length > 0 && val.length < 5000) {
                log('  GETTER', name + ' => ' + String(val).substring(0, 200).replace(/\n/g, '\\n'));
              }
            } catch (e2) {}
          }
        }
        return inst;
      };
    });
    log('HOOK', 'f8.$init — will test getters on next msg insert');
  } catch (e) {
    log('ERR', 'f8 constructor hook: ' + e.message);
  }

  log('DONE', 'Probe complete — check [GETTER] lines for actual message content');
});
