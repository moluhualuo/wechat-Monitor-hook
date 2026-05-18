/*
 * WeChat contact nickname probe
 *
 * Purpose:
 * 1. Hook real contact lookup on com.tencent.mm.storage.l3
 * 2. Print one contact hit, then stop
 * 3. Help convert the findings back into stable libxposed hooks
 */
'use strict';

function log(tag, msg) {
  console.log('[' + tag + '] ' + msg);
}

function shortText(value, maxLen) {
  var text = '';
  try {
    text = value === null || value === undefined ? '' : String(value);
  } catch (e) {
    text = '<toString-failed:' + e + '>';
  }
  text = text.replace(/\r/g, ' ').replace(/\n/g, ' ').trim();
  if (text.length > maxLen) {
    return text.slice(0, maxLen) + '...';
  }
  return text;
}

function fieldValue(instance, fieldName) {
  try {
    var clazz = instance.getClass();
    while (clazz !== null) {
      try {
        var field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        var value = field.get(instance);
        return shortText(value, 200);
      } catch (e) {
        clazz = clazz.getSuperclass();
      }
    }
  } catch (e2) {
  }
  return '';
}

function methodValue(instance, methodName) {
  try {
    var clazz = instance.getClass();
    var method = clazz.getMethod(methodName, []);
    method.setAccessible(true);
    var value = method.invoke(instance, []);
    return shortText(value, 200);
  } catch (e) {
    return '';
  }
}

function dumpFields(instance, names) {
  var result = [];
  for (var i = 0; i < names.length; i++) {
    var value = fieldValue(instance, names[i]);
    if (value) {
      result.push(names[i] + '=' + value);
    }
  }
  return result.join(', ');
}

function dumpMethods(instance, names) {
  var result = [];
  for (var i = 0; i < names.length; i++) {
    var value = methodValue(instance, names[i]);
    if (value) {
      result.push(names[i] + '()=' + value);
    }
  }
  return result.join(', ');
}

Java.perform(function () {
  log('PROBE', 'Java VM ready');

  var storageClassName = 'com.tencent.mm.storage.l3';
  var contactBaseClassName = 'com.tencent.mm.contact.r';

  try {
    var storageUse = Java.use(storageClassName);
    var contactUse = Java.use(contactBaseClassName);
    var done = false;

    storageUse.n.overload('java.lang.String', 'boolean').implementation = function (talker, strict) {
      if (done) {
        return storageUse.n.overload('java.lang.String', 'boolean').call(this, talker, strict);
      }
      var result = storageUse.n.overload('java.lang.String', 'boolean').call(this, talker, strict);
      done = true;
      log('CONTACT-HIT', 'talker=' + shortText(talker, 120)
        + ' strict=' + strict
        + ' class=' + (result ? result.getClass().getName() : 'null')
        + ' fields=[' + (result ? dumpFields(result, [
          'field_username', 'field_encryptUsername', 'field_talker', 'field_userName',
          'field_conRemark', 'field_nickname', 'field_alias'
        ]) : '') + '] methods=[' + (result ? dumpMethods(result, ['e1', 'u0', 'h2', 'O0']) : '') + ']');
      return result;
    };

    contactUse.h2.overloads.forEach(function (ov) {
      if (ov.argumentTypes.length !== 0) {
        return;
      }
      ov.implementation = function () {
        if (done) {
          return ov.call(this);
        }
        var value = ov.call(this);
        done = true;
        log('CONTACT-NAME', 'h2 username=' + shortText(methodValue(this, 'e1') || fieldValue(this, 'field_username'), 120)
          + ' value=' + shortText(value, 200));
        return value;
      };
    });
  } catch (e) {
    log('CONTACT-ERR', e.stack || e);
  }

  log('DONE', 'nickname probe installed');
});
