import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # updateMyInfo 载荷契约（阶段 L-1，K-1 提权修复的前端配套锁）
 *
 * 后端 `/user/updateMyInfo` 是「非 null 即覆盖」的合并语义：K-1 在服务端把
 * roleList/departmentList/storageList/factoryList 与 phone/email/realName/idCard
 * 强制清空作为兜底。但前端同样不该把整份当前用户对象原样上送——
 * 那等于每次改头像都携带一份完整授权快照过网（日志/代理/浏览器历史都可能留痕），
 * 且一旦未来有人调整后端清空清单，最坏路径会被前端"顺手激活"。
 *
 * 锁的判据：全仓不存在 `updateMyInfo(AppConfig.currentUser…)` 形态的调用；
 * 头像流程必须走「最小 payload 实体」。新增调用点想传大对象，先过这条 CI。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')

function collect(dir: string, exts: string[], out: string[] = []): string[] {
  if (statSync(dir, { throwIfNoEntry: false })?.isDirectory() !== true) {
    return out
  }
  for (const name of readdirSync(dir)) {
    if (name === '__tests__' || name === 'node_modules') {
      continue
    }
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      collect(full, exts, out)
    }
    else if (exts.some(ext => name.endsWith(ext))) {
      out.push(full)
    }
  }
  return out
}

const read = (path: string): string => readFileSync(path, 'utf-8').replace(/\r\n/g, '\n')

describe('updateMyInfo 载荷最小化契约', () => {
  const files = collect(join(ROOT, 'src'), ['.vue', '.ts'])

  it('全仓不得以整份 AppConfig.currentUser 作为 updateMyInfo 载荷', () => {
    const offenders = files
      .map(f => [f, read(f)] as const)
      .filter(([, source]) => /updateMyInfo\(\s*AppConfig\.currentUser/.test(source))
      .map(([f]) => f)
    expect(offenders, `以下文件把整份当前用户对象传给 updateMyInfo：${offenders.join(', ')}`).toEqual([])
  })

  it('updateMyInfo 的调用点必须只出现一个调用（新增改绑流程需走专用端点而非本接口）', () => {
    const hits = files
      .map(f => [f, read(f)] as const)
      .flatMap(([f, source]) => (source.match(/\.updateMyInfo\(/g) || []).map(() => f))
    // 模型层定义方法本身不计入调用（UserService.ts 里的 `async updateMyInfo(`）
    const callSites = hits.filter(f => !f.endsWith(join('model', 'personnel', 'user', 'UserService.ts')))
    expect(callSites.length).toBe(1)
  })

  it('头像流程必须先构造最小实体再提交（防止有人回退成整对象）', () => {
    const source = read(join(ROOT, 'src', 'component', 'UserCard.vue'))
    expect(source).toMatch(/const payload = new UserEntity\(\)/)
    expect(source).toMatch(/updateMyInfo\(payload\)/)
    expect(source).not.toMatch(/AppConfig\.currentUser\.value\.avatar\s*=/)
  })
})
