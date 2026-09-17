/**
 * # Java 源码口径的唯一实现（两个门禁共用）
 *
 * 为什么单独成文件：`check-airpower-backend.mjs`（棘轮）与 `check-java-imports.mjs`（导入可达性）
 * 都要"剥注释后看代码"，**口径必须一模一样** —— 各自抄一份的结果是两边数字对不上，
 * 而这两个数字正是退场进度的依据。改口径只改这里。
 */

function stripJavaComments(code) {
  let out = ''
  let state = 'code' // code | line | block | string | char
  for (let i = 0; i < code.length; i += 1) {
    const c = code[i]
    const next = code[i + 1]
    if (state === 'code') {
      if (c === '/' && next === '/') {
        state = 'line'
        i += 1
        continue
      }
      if (c === '/' && next === '*') {
        state = 'block'
        i += 1
        continue
      }
      if (c === '"') {
        state = 'string'
      }
      else if (c === '\'') {
        state = 'char'
      }
    }
    else if (state === 'line') {
      if (c === '\n') {
        state = 'code'
        out += c
      }
      continue
    }
    else if (state === 'block') {
      if (c === '*' && next === '/') {
        state = 'code'
        i += 1
      }
      else if (c === '\n') {
        out += c
      }
      continue
    }
    else if (state === 'string' || state === 'char') {
      if (c === '\\') {
        out += c + (next ?? '')
        i += 1
        continue
      }
      if ((state === 'string' && c === '"') || (state === 'char' && c === '\'')) {
        state = 'code'
      }
    }
    out += c
  }
  return out
}

export { stripJavaComments }
