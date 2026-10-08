#!/usr/bin/env node
/**
 * 错误码语义门禁（403 不是"万能失败码"）
 * ============================================================================
 * 为什么要有这个脚本：本仓把「授权失败」和一大票**别的东西**都渲染成 403 家族
 * （`Errors` 里 4031..4039 被注释归为"403 无权限代码"，但语义其实是
 * 已存在 / 使用中 / 文件过大 / 已禁用 / 系统繁忙）。后果有两层：
 *
 *   1. **监控与排障被引向错路**：一次"引用了已删除的库存"被记成越权尝试，
 *      值班的人去查角色配置，而真正的原因是数据缺口；
 *   2. **前端只能照码给指引**：403 的用户提示是"请联系管理员开通权限"，
 *      用在"文件太大""数据已存在"上就是**假指引** —— 用户照着做也解决不了，还白跑一趟。
 *      （前端已按文案二次分流兜住，见 `src/api/errorPolicy.ts`；但源头写对才是根治。）
 *
 * 历史真实案例（都已修，本脚本负责不让它们回来）：
 *   - `OutputService`：`FORBIDDEN.whenNull(inventory, "未找到指定的出库来源库存")` → 应为 404；
 *   - `MoveService`：`FORBIDDEN.whenNull(material, "来源库存物料信息缺失")` → 应为 404（同方法上一行就是 404）；
 *   - `AbstractBaseBillService`：`FORBIDDEN.whenNull(status, "没有找到…完成状态")` → 服务端没配字典，应为 500；
 *   - `TenantContextResolver`：`FORBIDDEN.show("非法的租户标识：…")` → 输入不合法，应为 4002。
 *
 * ## 三条规则（都要求"消息里已经能看出不是权限问题"，所以误报面很窄）
 *   R1 查找失败/输入非法不得用 FORBIDDEN：消息含 未找到 / 没有找到 / 缺失 / 非法 / 格式 / 不能为空；
 *   R2 `FORBIDDEN*.whenNotEquals(a, b, …)` 的两个参数必须与**当前用户身份**有关
 *      （`getCurrentUser*` / `currentUser` / `getUserId` / `userId`）——
 *      否则它比的是"调用方给的两个值互相对不上"，属输入自相矛盾 → PARAM_INVALID(4002)；
 *   R3 反向：`PARAM_MISSING` / `PARAM_INVALID` 的消息**主句**不得是权限断言
 *      （句首或标点后的 无权 / 没有权限 / 未授权 / 权限不足）—— 把越权报成参数错误，
 *      会让它从权限告警里消失，前端也会给"检查提交内容"的错误指引。
 *      顺带提一句"或无权访问"作为**可能原因**的不算（见 `BaseService` 脱敏写回守卫，自检里钉住了）。
 *
 * ## 刻意**不**拦的（已评审保留，理由写在 docs/reports/DESIGN_THINKING.md）
 *   - 状态机/业务规则拒绝（`该单据状态无法审核`、`令牌数量已达上限`、`该明细已标记完成…`）继续用 403：
 *     它们是"你现在的处境不允许"，前端已归为「操作未被允许」并给"按提示调整后重试"，不会给假指引；
 *     真要改成 409 是一次跨前后端与监控口径的语义迁移，不该混在门禁里顺手做。
 *   - 认证路径上的 `FORBIDDEN`（`权限不存在` / `无效的令牌类型` / `不支持的令牌类型`）：
 *     改码会牵动登录与令牌校验的既有行为，单独评估。
 *
 * 用法：node infra/scripts/check-error-code-usage.mjs [--verbose]
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const SERVER = join(ROOT, 'SPMS-Server', 'src', 'main', 'java')
const VERBOSE = process.argv.includes('--verbose')

/** R1：消息里这些词说明失败原因是"数据不在/输入不对"，不是"你没权限" */
const LOOKUP_OR_INPUT_WORDING = /未找到|没有找到|缺失|非法|格式|不能为空/
/** R2：所有权校验的标志（比较的一方来自当前登录身份） */
const CURRENT_IDENTITY = /getCurrentUser|currentUser|getUserId|userId|getCreateUser/
/**
 * R3 的权限措辞：**必须是主句**，不能只是顺带提一句可能原因。
 *
 * 真实反例（`BaseService` 脱敏写回守卫）：
 * `PARAM_INVALID.show("记录不存在或无权访问，敏感字段的脱敏值无法安全处理，请刷新后重试")`
 * —— 失败性质是"你提交的载荷没法安全处理，刷新后重试"（4002 正确），
 * "或无权访问"只是解释行读不到的**可能原因之一**。若按裸词匹配就会逼着这句真文案去迁就规则，
 * 那是尾巴摇狗。所以只认句首或标点后的权限断言。
 */
const PERMISSION_DENIAL_CLAUSE = /(?:^|[，。；：、!?！？\s])(?:你|您)?\s*(?:无权|没有权限|未授权|权限不足)/
/** FORBIDDEN 家族的调用：`FORBIDDEN.show(` / `FORBIDDEN_EDIT.whenNotEquals(` … */
const CALL = /\b(FORBIDDEN(?:_[A-Z_]+)?)\s*\.\s*(when\w*|show)\s*\(/g
/** 参数码调用（R3 用） */
const PARAM_CALL = /\b(PARAM_MISSING|PARAM_INVALID)\s*\.\s*(when\w*|show)\s*\(/g

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) {
      walk(p, out)
    }
    else if (name.endsWith('.java')) {
      out.push(p)
    }
  }
  return out
}

/**
 * 去注释但**保留行数**：块注释按字符挖空、整行注释清空。
 * 保留行数是为了报错时给出的行号仍然对得上源文件（否则一次块注释就让后面全部错位）。
 */
function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, matched => matched.replace(/[^\n]/g, ' '))
    .replace(/^[ \t]*\/\/.*$/gm, '')
}

/** 从调用起点取到语句结束（`;`），最多 400 字符，避免跨方法误吞 */
function statementOf(source, from) {
  const end = source.indexOf(';', from)
  const stop = end < 0 ? Math.min(source.length, from + 400) : Math.min(end + 1, from + 400)
  return source.slice(from, stop)
}

/** 语句里的第一个字符串字面量（`String.format("…%s…", x)` 也能取到格式串） */
function firstLiteral(statement) {
  const matched = /"((?:[^"\\]|\\.)*)"/.exec(statement)
  return matched ? matched[1] : ''
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length
}

/**
 * 扫一个文件，返回违规清单。
 *
 * @param src 已去注释的源码
 * @param relFile 相对仓库根的路径（报错用）
 */
function scan(src, relFile) {
  const violations = []
  const stats = { forbiddenCalls: 0, paramCalls: 0 }

  for (const match of src.matchAll(CALL)) {
    stats.forbiddenCalls += 1
    const [, constant, method] = match
    const statement = statementOf(src, match.index)
    const message = firstLiteral(statement)
    const at = `${relFile}:${lineOf(src, match.index)}`

    // R1：查找失败 / 输入非法 冒充授权失败
    if (LOOKUP_OR_INPUT_WORDING.test(message)) {
      const hit = LOOKUP_OR_INPUT_WORDING.exec(message)[0]
      violations.push(
        `R1 ${at}  ${constant}.${method}(… "${message}")\n`
        + `       消息里的「${hit}」说明失败原因是数据缺失或输入不合法，不是授权边界。\n`
        + `       改法：查不到行 → DATA_NOT_FOUND(404)；输入自相矛盾 → PARAM_INVALID(4002)；服务端配置缺失 → SERVICE_ERROR(500)。`,
      )
    }

    // R2：whenNotEquals 比的不是"当前用户 vs 数据归属"，那就是输入校验
    if (/^whenNotEquals/i.test(method) && !CURRENT_IDENTITY.test(statement)) {
      violations.push(
        `R2 ${at}  ${constant}.${method}(…) 两个参数都与当前登录身份无关\n`
        + `       这是「调用方给的两个值互相对不上」的输入校验，用 403 会让监控记成越权尝试。\n`
        + `       改法：PARAM_INVALID.whenNotEquals(a, b, "…")；确属所有权校验时让参数显式引用 getCurrentUser*()。`,
      )
    }
  }

  for (const match of src.matchAll(PARAM_CALL)) {
    stats.paramCalls += 1
    const [, constant, method] = match
    const message = firstLiteral(statementOf(src, match.index))
    if (PERMISSION_DENIAL_CLAUSE.test(message)) {
      violations.push(
        `R3 ${relFile}:${lineOf(src, match.index)}  ${constant}.${method}(… "${message}")\n`
        + `       消息的主句在讲权限，却用参数码上报：越权会从权限告警里消失，前端也会给"检查提交内容"的错误指引。\n`
        + `       改法：FORBIDDEN(403)；数据权限拒绝统一走 DataPermissionDenial.denied(...)。\n`
        + `       （若权限只是"顺带提一句可能原因"，请把主句改成描述用户该做什么，如"请刷新后重试"。）`,
      )
    }
  }

  return { violations, stats }
}

// ============================================================================
// 门禁自检：证明三条规则都真的能咬人，且不误伤已评审保留的写法
// ============================================================================
function selfCheck() {
  const problems = []
  let cases = 0
  const expectViolation = (label, src, rulePrefix, wanted) => {
    cases += 1
    const got = scan(stripComments(src), 'selfcheck.java').violations.filter(v => v.startsWith(rulePrefix))
    if (wanted === 0) {
      if (got.length !== 0) {
        problems.push(`自检「${label}」失败：不该被拦，却拦了 ${got.length} 处`)
      }
      return
    }
    if (got.length !== wanted) {
      problems.push(`自检「${label}」失败：期望 ${wanted} 处 ${rulePrefix}，实际 ${got.length} 处`)
    }
  }

  // R1 必须拦：查不到行却报 403（OutputService 的历史写法）
  expectViolation('R1 咬人', `
    InventoryEntity inventory = inventoryService.get(id);
    FORBIDDEN.whenNull(inventory, "未找到指定的出库来源库存");
  `, 'R1', 1)

  // R1 必须拦：服务端配置缺失却报 403（AbstractBaseBillService 的历史写法）
  expectViolation('R1 咬人（配置缺失）', `
    IDictionary status = getFinishedStatus();
    FORBIDDEN.whenNull(status, "标记完成失败，没有找到完成状态");
  `, 'R1', 1)

  // R1 必须拦：输入非法却报 403（TenantContextResolver 的历史写法）
  expectViolation('R1 咬人（非法输入）', `
    FORBIDDEN.show("非法的租户标识：" + raw);
  `, 'R1', 1)

  // R1 不该拦：真授权失败（拦截器与数据权限的真实文案）
  expectViolation('R1 不误伤授权', `
    FORBIDDEN.show(String.format("你无权访问 %s (%s)", label, code));
    FORBIDDEN.show("数据权限不足，请缩小查询范围后重试");
  `, 'R1', 0)

  // R1 不该拦：状态机拒绝（已评审保留）
  expectViolation('R1 不误伤状态机', `
    FORBIDDEN.when(!canAudit(bill), "该单据状态无法审核");
    FORBIDDEN.when(list.size() >= MAX_TOKENS_PER_USER, "私人令牌数量已达上限");
    FORBIDDEN.when(detail.getIsFinished(), "该明细已标记完成，无法再添加明细完成数量");
  `, 'R1', 0)

  // R1 不该被注释触发（注释里提到历史写法不算违规）
  expectViolation('R1 不看注释', `
    // 历史写法：FORBIDDEN.whenNull(inventory, "未找到指定的出库来源库存")
    /* 块注释里也一样：FORBIDDEN.show("非法的租户标识") */
    DATA_NOT_FOUND.whenNull(inventory, "未找到指定的出库来源库存");
  `, 'R1', 0)

  // R2 必须拦：比的是两个业务值（InputService/OutputService 的历史写法）
  expectViolation('R2 咬人', `
    FORBIDDEN.whenNotEquals(inventory.getMaterial().getId(), materialId, "物料信息不匹配");
  `, 'R2', 1)

  // R2 不该拦：所有权校验（PersonalTokenController 的真实写法）
  expectViolation('R2 不误伤所有权校验', `
    FORBIDDEN_EDIT.whenNotEquals(exist.getUser().getId(), getCurrentUserId(), "你没有权限修改此令牌");
  `, 'R2', 0)

  // R3 必须拦：越权被报成参数错误
  expectViolation('R3 咬人', `
    PARAM_INVALID.show("你没有权限查看此数据");
  `, 'R3', 1)

  // R3 不该拦：权限只是"顺带提一句可能原因"，主句讲的是提交内容无法处理（BaseService 脱敏写回守卫的真实文案）
  expectViolation('R3 不误伤脱敏写回守卫', `
    PARAM_INVALID.show("记录不存在或无权访问，敏感字段的脱敏值无法安全处理，请刷新后重试");
  `, 'R3', 0)

  // R3 不该拦：正常的参数错误文案
  expectViolation('R3 不误伤参数错误', `
    PARAM_INVALID.whenNotEquals(a.getId(), b.getId(), "物料信息不匹配");
    PARAM_MISSING.show("请传入出库来源库存信息");
  `, 'R3', 0)

  return { problems, cases }
}

// ============================================================================

if (!statSync(SERVER).isDirectory()) {
  console.error(`✗ 找不到后端源码目录：${SERVER}`)
  process.exit(1)
}

const files = walk(SERVER)
if (!files.length) {
  console.error('✗ 没扫到任何 .java —— 门禁空转')
  process.exit(1)
}

const { problems: selfProblems, cases: selfCases } = selfCheck()
if (selfProblems.length) {
  for (const p of selfProblems) {
    console.error(`✗ ${p}`)
  }
  process.exit(1)
}

const all = []
let forbiddenCalls = 0
let paramCalls = 0
let touchedFiles = 0
for (const file of files) {
  const src = stripComments(readFileSync(file, 'utf8'))
  const { violations, stats } = scan(src, relative(ROOT, file).replaceAll('\\', '/'))
  forbiddenCalls += stats.forbiddenCalls
  paramCalls += stats.paramCalls
  if (stats.forbiddenCalls || stats.paramCalls) {
    touchedFiles += 1
  }
  all.push(...violations)
}

if (!forbiddenCalls) {
  console.error('✗ 一处 FORBIDDEN 调用都没扫到 —— 解析逻辑失效（门禁不能空转放行）')
  process.exit(1)
}

console.log(`已核对 ${files.length} 个 Java 文件、${touchedFiles} 个涉及错误码的文件`)
console.log(`  · FORBIDDEN 家族调用 ${forbiddenCalls} 处，PARAM_MISSING/PARAM_INVALID 调用 ${paramCalls} 处`)
console.log(`  · 自检 ${selfCases} 项全部通过（三条规则都能咬人，且不误伤授权/状态机/所有权/注释四类写法）`)
if (VERBOSE) {
  console.log('  · 规则：R1 查找失败或输入非法不得用 403；R2 whenNotEquals 必须与当前身份有关；R3 参数码不得承载权限措辞')
}

if (all.length) {
  console.error(`\n✗ ${all.length} 处错误码语义误用：`)
  for (const v of all) {
    console.error(`  - ${v}`)
  }
  console.error('\n  为什么要管：403 会被监控记成越权尝试，前端会照着码给"请联系管理员开通权限"的指引 ——')
  console.error('  用在"数据不存在""文件太大""状态不允许"上，用户与值班的人都会被引向错路。')
  process.exit(1)
}

console.log('✓ 403 家族只承载「授权失败」与已评审保留的「业务规则拒绝」，无查找失败/输入错误冒充')
