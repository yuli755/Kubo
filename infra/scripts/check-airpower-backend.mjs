#!/usr/bin/env node
/**
 * # 后端 airpower 足迹盘点 + 棘轮门禁
 *
 * ## 为什么需要它
 * 前端 `@airpower/web` 退场之所以能"一次删净 → 成批修复"，靠的是 `vue-tsc` 能在十几秒内
 * 把**所有缺口**列成一张表。后端完整编译需要 JDK 17 和可达的 Maven 仓库；
 * 此脚本只盘点耦合，不代替编译（独立工具可另跑 `pnpm java:unit`）。
 *
 * 这个脚本就是后端版的"缺口清单生成器"：扫 Java / pom / yml，把 airpower 的用法按
 * **符号**（要自建什么）与**模块**（core / curd / api / redis / email / exception …）分类汇总。
 * 它不做任何修改，只回答两个问题：
 *   1. 现在还有多少 airpower？（文件数 / 符号数 / 引用次数 / 依赖 / 配置键）
 *   2. 要脱离它，必须自建或替换哪些东西？（逐符号用量表 = 优先级）
 *
 * ## 棘轮（`--check`）
 * 后端退场是长跑，途中不能"越删越多"。`--check` 与 BASELINE 比对：**只许降**。
 * 某一批真正删掉了若干符号后，把 BASELINE 改成新数字（这是有意的代码改动，会被 review）。
 * 已退场的工具另外执行零回潮检查，不能用删掉别处引用来抵消重新引入旧工具。
 *
 * ## 用法
 * ```
 * pnpm airpower:backend              # 人读盘点
 * pnpm airpower:backend -- --json    # 给脚本/CI 读
 * pnpm airpower:backend -- --check   # 棘轮：超过基线即 exit 1（CI 用）
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { INDEPENDENT_DOMAIN_SOURCES } from './backend-independent-sources.mjs'
import { pomDependencyDeclarations } from './backend-pom.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SERVER = path.join(ROOT, 'SPMS-Server')

/**
 * 基线（**只许降**）。每完成一批后端自建，把这里改成新数字（改动可见、可 review）。
 * 数字膨胀时 `--check` 会红，并告诉你该回去看哪一批把 airpower 用回来了。
 *
 * 演进：
 * - 2026-09-18 起点：180 文件 / 73 符号 / 656 引用（依赖 airpower-curd + airpower-email + parent POM）
 * - 2026-09-18 第一批（邮件 + Cookie 自建）：183 / 72 / 655
 *   依赖 −1（airpower-email 摘除）、模块 −2（email 归零、cookie 3→1）。
 *   **文件数反而 +3 是预期内的**：自建件本身会引用框架符号（`EmailHelper` 还引用 `Errors.EMAIL_ERROR`；
 *   `Application` 里那行 `exclude = cn.hamm.airpower.cookie.Auto.class` 是显式的接管声明）——
 *   退场是"引用集中到少数适配点、再一次性拔掉"，不是"每批都让所有数字下降"。
 * - 2026-09-18 第二批（B0 摘父 POM + B1 错误码层自持）：174 / 60 / 603
 *   · B0 只动 pom（不再继承 cn.hamm:airpower）、不改代码，所以数字不动；
 *   · B1 把 `cn.hamm.airpower.exception.Errors` **逐行等价**搬进
 *     `cn.hamm.spms.common.exception.Errors`，替换 49 个文件的静态导入 ——
 *     **exception 模块整块归零**（55 → 0）。
 *   · core 285（+3）：新 Errors 引用 core 的 IException / IDictionary / ServiceException，
 *     这三样是 B2 的活（接口与异常运行时），错不在这一批。
 * - 2026-09-20 B2a 工具层：168 / 49 / 568（新增接线测试含 1 处旧异常类型引用）。
 *   8 个工具类 + ITree 的直接用法清零；6 个无依赖生产类通过真实 javac 17 / 32 项契约。
 *   全量 Maven 接线仍待验收：沙箱 Maven 下载不可达，GitHub CI 因账户额度未启动。
 *   下调基线只锁住当前足迹，不代表 B2 或整个后端退场已经完成。
 * - 2026-09-20 B2b CSV/反射：168 / 47 / 556。
 *   14 处 CollectionUtil + 3 处 ReflectUtil 移除；元数据适配点 3 处 + 接线测试 2 处注解引用如实保留。
 *   9 个无依赖生产类 / 64 契约通过；全量源码另过 javac AST（不是完整类型检查）。
 *   JPA 回退仍调用旧框架内部导出，完整 Maven 接线仍待验收，不能按直接引用清零就删依赖。
 * - 2026-09-20 B2c 枚举/任务残留：162 / 46 / 546。
 *   5 个普通业务枚举不再实现旧 IDictionary；TaskUtil 直接用法清零，清掉 3 个无用导入。
 *   9 个工具 + 5 个实际业务枚举 + AppConstant 共 15 个生产源码真实编译，79 契约通过。
 *   @Dictionary 校验枚举、状态接口、令牌算法、Json/异常运行时仍未迁，Maven 完整接线待验收。
 * - 2026-09-20 B3a 电话约束/无用标记：161 / 44 / 544。
 *   三个电话字段改标准 Pattern（其中 UserEntity 的 Phone 被通配导入隐藏）；错误/配置枚举移除 3 个无用字典标记。
 *   UserEntity 通配导入展开为 4 个真实注解引用（+3），不把显式化的增量藏起来；本批净减 2 引用。
 *   16 个生产源码 / 91 独立契约；完整 Jakarta Validation 与应用接线仍需 Maven 验收。
 * - 2026-09-21 B2d 内部 JSON 文本：160 / 44 / 540。
 *   事件发布/接收、Outbox、审计快照的 4 个旧 Json 导入移除；HTTP Json 与异常运行时仍在。
 *   正式 Jackson 2.18.3 / Lombok 1.18.36 经校验后真实编译 5 个生产源码、通过 25 项 JSON 契约。
 *   完整 Maven / Redis / HTTP 验收仍未完成；不能把部分依赖可取得写成整仓已编译。
 * - 2026-09-21 B4a 读侧分页收口：160 / 44 / 518。
 *   14 服务不再直接持有 Page/Sort（-28）；现有 BaseService 明示 3 个 DTO 依赖、接线测试新增 3 处。
 *   ReadPage 独立持有分页计算，旧请求/响应/JPA 运行时仍保留；不是查询模型整体清零。
 * - 2026-09-21 B7a Cookie 模块：159 / 43 / 517。
 *   已自建 Cookie 的真实 Spring/Servlet 切片通过；curd 的 cookie 传递依赖被排除，启动类不再引用旧 Auto。
 *   POM exclusion 与 dependency 分开报告；完整应用启动/实际 Maven 依赖树仍待验收。
 * - 2026-09-21 P1 业务异常/错误响应：155 / 42 / 511。
 *   Errors/CustomError 不再继承旧 IException；业务失败改自持异常，HTTP 保持 200 + 业务码。
 *   旧 Json 只在明确响应桥接回旧过滤链；登录保留新旧异常两类失败计数，未声称整个 HTTP/鉴权已退场。
 * - 2026-09-21 P1b 密码散列与自持成功响应：153 / 42 / 500。
 *   PasswordHasher 自持 SHA-1 双向加盐散列，UserService/DevDataInitRunner 脱离 PermissionUtil。
 *   DataResource/DataFieldPolicy/Dashboard/ErrorReport/CodeRule/Role/Base/BaseBill 控制器切自持 ApiResponse。
 *   UserController 的 Redis 缓存改用 JsonCodec；DevDataInitRunner 完全脱离 AirPower。
 * - 2026-09-21 P1c 读侧统计与权限扫描收敛：153 / 40 / 500。
 *   十四资源 getCount/getStat 切自持 ApiResponse；PersonalToken/File 控制器完全脱离 Json（零 Json 控制器增至 10 个）。
 *   PermissionScanner 接管权限元数据扫描，PermissionUtil 与 IPermission 全量退场归零（符号 42→40）。
 *   自持 QueryPageRequest/QueryListRequest/QueryRequest/Page/Sort/QueryPageResponse 落地并通关 136 项契约。
 * - 2026-09-21 P2 核心继承链自持替换：152 / 38 / 492。
 *   BaseEntity、BaseRepository、BaseService、BaseController、RequestInterceptor 全量脱离 AirPower 基类。
 *   永久退场 5 大基类并加入防回潮清单：CurdEntity、ICurdRepository、CurdService、CurdController、CurdRequestInterceptor。
 *   DepartmentController、MenuController 脱离 Json，零 Json 控制器增至 12 个。
 *   全量 491 个 Java 源码通过 javac 17 AST 语法解析；138 项纯 JDK 契约测试全通。
 * - 2026-09-21 P3 元数据注解与查询模型自持替换：58 / 27 / 135。
 *   @Description、@Api、@Meta、@Search、@ReadOnly、@Export、@Dictionary、IDictionary、@NullEnable、@SearchEmpty 全量替换为自持实现。
 *   全量业务与测试代码中的 Page、Sort、QueryPageRequest、QueryListRequest 清空，全部收敛到自持 query 包。
 *   后端 AirPower 引用从 391 处骤降至 132 处（文件 57 个，已脱离 70%）。
 * - 2026-09-21 P4 Curd 模块与通用工具全量退场：3 / 2 / 5。
 *   Curd、@Extends、ExportHelper、Permission、AccessConfig、ApiConfig、ApiController、RequestFilter、QueryExport 全量自持替换。
 *   curd 模块引用彻底归零（86 → 0），api 模块彻底归零（4 → 0）。
 *   全仓生产代码仅剩 UserController 与 LegacyApiResponseBridge 保留协议兼容 Json/ServiceException 边界，测试保留契约断言；138 项纯 JDK 契约与 517 个源码 AST 解析全通。
 */
const BASELINE = { files: 3, symbols: 2, references: 5 }

/** 已迁走的符号不能回潮（包括静态成员导入），与总量预算分开判断。 */
const RETIRED_SYMBOLS = [
  ...[
    'CollectionUtil', 'ReflectUtil', 'DateTimeUtil', 'DictionaryUtil', 'FileUtil', 'NumberUtil', 'RandomUtil',
    'StringUtil', 'TraceUtil', 'TreeUtil', 'TaskUtil', 'AccessTokenUtil', 'interfaces.ITree', 'interfaces.IException', 'annotation.Phone',
    'annotation.Description', 'annotation.Meta', 'annotation.ReadOnly', 'annotation.Export', 'annotation.Dictionary',
    'annotation.Desensitize', 'annotation.ExposeAll', 'enums.DesensitizeType',
  ].map(name => `cn.hamm.airpower.core.${name}`),
  'cn.hamm.airpower.api.annotation.Api',
  'cn.hamm.airpower.api.ApiController',
  'cn.hamm.airpower.api.config.ApiConfig',
  'cn.hamm.airpower.curd.annotation.Search',
  'cn.hamm.airpower.curd.annotation.NullEnable',
  'cn.hamm.airpower.curd.annotation.SearchEmpty',
  'cn.hamm.airpower.curd.annotation.Extends',
  'cn.hamm.airpower.curd.permission.Permission',
  'cn.hamm.airpower.curd.permission.PermissionUtil',
  'cn.hamm.airpower.curd.permission.IPermission',
  'cn.hamm.airpower.curd.base.Curd',
  'cn.hamm.airpower.curd.base.CurdEntity',
  'cn.hamm.airpower.curd.base.ICurdRepository',
  'cn.hamm.airpower.curd.base.CurdService',
  'cn.hamm.airpower.curd.base.CurdController',
  'cn.hamm.airpower.curd.interceptor.CurdRequestInterceptor',
  'cn.hamm.airpower.curd.interceptor.filter.RequestFilter',
  'cn.hamm.airpower.curd.config.AccessConfig',
  'cn.hamm.airpower.curd.config.CurdConfig',
  'cn.hamm.airpower.curd.config.ExportConfig',
  'cn.hamm.airpower.curd.helper.ExportHelper',
  'cn.hamm.airpower.curd.helper.TransactionHelper',
  'cn.hamm.airpower.curd.model.query.QueryExport',
  'cn.hamm.airpower.redis.RedisHelper',
]


/**
 * 剥掉 Java 注释、保留字符串字面量。
 *
 * 为什么必须剥：退场过程中大量注释会写"这里原来是 airpower 的 EmailHelper"之类的说明，
 * 不剥的话**自己写的档案会把数字顶上去**（本轮就发生过：摘掉 airpower-email 反而 +3 个文件）。
 * 而 `startsWith("cn.hamm.airpower.")` 这种**字符串**是真耦合（判断类归属），必须保留。
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

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'target' || entry.name === 'node_modules' || entry.name === '.git') {
      continue
    }
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, files)
    }
    else {
      files.push(full)
    }
  }
  return files
}

/** airpower 的顶层模块（`cn.hamm.airpower.<module>`）→ 人话说明，用于分组展示 */
const MODULES = {
  api: 'HTTP 入口层：@Api 注解、ApiController 基类、ApiConfig',
  core: '内核工具与元数据注解：Json / AccessTokenUtil / @Description / @Meta / IDictionary …',
  curd: 'CRUD 引擎：Curd 端点、@Search / @Extends 元数据、分页请求响应、Permission、ExportHelper',
  redis: 'Redis 帮助类',
  cookie: 'Cookie 配置与帮助类',
  email: '邮件帮助类',
  exception: '错误码（Errors）',
}

const javaFiles = fs.existsSync(path.join(SERVER, 'src')) ? walk(path.join(SERVER, 'src')).filter(f => f.endsWith('.java')) : []

const SYMBOL_RE = /cn\.hamm\.airpower\.[a-zA-Z0-9_.]*/g

/**
 * 符号级的足迹。
 *
 * 注意两处口径（都踩过）：
 * - 结尾的点要剥掉：`OperationLogSnapshot` 里有一句 `startsWith("cn.hamm.airpower.")` 的
 *   **字符串**（判断"这个类是框架类还是业务类"），它不是 import，但确实是耦合点，照样计数；
 * - 同一个符号在一行里出现多次只记多次（用量 = 改动面大小）。
 */
const symbolHits = new Map() // symbol → 次数
const moduleHits = new Map() // module → 次数
const fileHits = []
const retiredUsages = []
const frameworkUsages = []
const independentUsages = []
const independentFiles = new Set(INDEPENDENT_DOMAIN_SOURCES)
const dictionaryFreeFiles = new Set([
  'SPMS-Server/src/main/java/cn/hamm/spms/common/exception/Errors.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/common/exception/CustomError.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/config/enums/ConfigFlag.java',
])
const wildcardUsages = []
const mapperPagingFiles = new Set([
  'SPMS-Server/src/main/java/cn/hamm/spms/module/asset/contract/ContractService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/asset/material/MaterialService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/channel/customer/CustomerService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/channel/sale/SaleService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/channel/purchase/PurchaseService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/channel/purchaseprice/PurchasePriceService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/channel/saleprice/SalePriceService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/channel/supplier/SupplierService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/factory/storage/StorageService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/factory/structure/StructureService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/wms/input/InputService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/wms/move/MoveService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/wms/inventory/InventoryService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/wms/output/OutputService.java',
])

const jsonFreeControllers = new Set([
  'SPMS-Server/src/main/java/cn/hamm/spms/common/errorreport/ErrorReportController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/dashboard/DashboardController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/controller/DataResourceController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/controller/DataFieldPolicyController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/coderule/CodeRuleController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/personnel/role/RoleController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/personnel/user/token/PersonalTokenController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/file/FileController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/base/BaseController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/base/bill/BaseBillController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/personnel/department/DepartmentController.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/menu/MenuController.java',
])
const passwordHasherConsumers = new Set([
  'SPMS-Server/src/main/java/cn/hamm/spms/module/personnel/user/UserService.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/DevDataInitRunner.java',
])
const jsonTextFiles = new Set([
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/event/DataPermissionEventPublisher.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/event/DataPermissionEventListener.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/service/PermissionChangeOutboxRecorder.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/service/OperationLogSnapshot.java',
])

const mentionOnly = []

for (const file of javaFiles) {
  const raw = fs.readFileSync(file, 'utf8')
  if (!raw.includes('cn.hamm.airpower')) {
    continue
  }
  const source = stripJavaComments(raw)
  const hits = source.match(SYMBOL_RE) ?? []
  if (hits.length === 0) {
    // 只在注释里提到（迁移档案）——不计入足迹，单列出来供人查阅
    mentionOnly.push(path.relative(ROOT, file).replaceAll('\\', '/'))
    continue
  }
  const relativeFile = path.relative(ROOT, file).replaceAll('\\', '/')
  fileHits.push({ file: relativeFile, count: hits.length })
  for (const [, target] of source.matchAll(/^\s*import\s+(?:static\s+)?(cn\.hamm\.airpower(?:\.[\w$]+)*\.\*)\s*;/gm)) {
    wildcardUsages.push({ file: relativeFile, target })
  }
  if (independentFiles.has(relativeFile)) {
    independentUsages.push(relativeFile)
  }
  if (relativeFile.startsWith('SPMS-Server/src/main/java/cn/hamm/spms/framework/')) {
    frameworkUsages.push(relativeFile)
  }
  for (const hit of hits) {
    const symbol = hit.replace(/\.$/, '')
    if (RETIRED_SYMBOLS.some(retired => symbol === retired || symbol.startsWith(`${retired}.`))
      || symbol === 'cn.hamm.airpower.cookie' || symbol.startsWith('cn.hamm.airpower.cookie.')
      || symbol === 'cn.hamm.airpower.core' || symbol === 'cn.hamm.airpower.core.interfaces'
      || symbol === 'cn.hamm.airpower.core.annotation'
      || (mapperPagingFiles.has(relativeFile) && ['Page', 'Sort'].some(name =>
        symbol === `cn.hamm.airpower.curd.model.query.${name}`
        || symbol.startsWith(`cn.hamm.airpower.curd.model.query.${name}.`)))
      || (jsonFreeControllers.has(relativeFile) && (symbol === 'cn.hamm.airpower.core.Json'
        || symbol.startsWith('cn.hamm.airpower.core.Json.')))
      || (passwordHasherConsumers.has(relativeFile) && (symbol === 'cn.hamm.airpower.curd.permission.PermissionUtil'
        || symbol.startsWith('cn.hamm.airpower.curd.permission.PermissionUtil.')))
      || (jsonTextFiles.has(relativeFile) && (symbol === 'cn.hamm.airpower.core.Json'
        || symbol.startsWith('cn.hamm.airpower.core.Json.')))
      || (dictionaryFreeFiles.has(relativeFile) && (symbol === 'cn.hamm.airpower.core.interfaces.IDictionary'
        || symbol.startsWith('cn.hamm.airpower.core.interfaces.IDictionary.')))) {
      retiredUsages.push({ file: relativeFile, symbol })
    }
    symbolHits.set(symbol, (symbolHits.get(symbol) ?? 0) + 1)
    const module = symbol.split('.')[3] ?? 'core'
    moduleHits.set(module, (moduleHits.get(module) ?? 0) + 1)
  }
}

// ── 依赖与配置面（同样是耦合，不能只数 import）──
const pomPath = path.join(SERVER, 'pom.xml')
const pom = fs.existsSync(pomPath) ? fs.readFileSync(pomPath, 'utf8') : ''
const pomDeclarations = pomDependencyDeclarations(pom)
const directDependencies = pomDeclarations.filter(item => !item.managed)
const pomDependencies = directDependencies.filter(item => item.groupId === 'cn.hamm' && item.artifactId?.startsWith('airpower')).map(item => item.artifactId)
const pomExclusions = directDependencies.flatMap(item => item.exclusions.map(exclusion => ({
  from: `${item.groupId}:${item.artifactId}`,
  groupId: exclusion.groupId,
  artifactId: exclusion.artifactId,
})))
const parentAirpower = /<parent>[\s\S]*?<artifactId>airpower<\/artifactId>/.test(pom.replace(/<!--[\s\S]*?-->/g, ''))

const ymlHits = []
for (const yml of walk(path.join(SERVER, 'src', 'main', 'resources')).filter(f => /\.ya?ml$/.test(f))) {
  const lines = fs.readFileSync(yml, 'utf8').split('\n')
  lines.forEach((line, index) => {
    if (/^\s*airpower\s*:/.test(line)) {
      ymlHits.push(`${path.relative(ROOT, yml).replaceAll('\\', '/')}:${index + 1}`)
    }
  })
}

const symbols = [...symbolHits.entries()]
  .map(([symbol, count]) => ({ symbol, count, module: symbol.split('.')[3] ?? 'core' }))
  .sort((a, b) => b.count - a.count || a.symbol.localeCompare(b.symbol))

const modules = [...moduleHits.entries()]
  .map(([module, count]) => ({ module, count, description: MODULES[module] ?? '（未归类）' }))
  .sort((a, b) => b.count - a.count)

const report = {
  generatedAt: new Date().toISOString(),
  totals: {
    javaFiles: javaFiles.length,
    airpowerFiles: fileHits.length,
    symbols: symbols.length,
    references: symbols.reduce((sum, s) => sum + s.count, 0),
    commentOnlyFiles: mentionOnly.length,
  },
  baseline: BASELINE,
  parentPom: parentAirpower,
  dependencies: [...new Set(pomDependencies)],
  exclusions: pomExclusions,
  configKeys: ymlHits,
  retiredSymbols: RETIRED_SYMBOLS,
  retiredUsages,
  frameworkUsages,
  independentSources: INDEPENDENT_DOMAIN_SOURCES,
  independentUsages,
  dictionaryFreeFiles: [...dictionaryFreeFiles],
  wildcardUsages,
  jsonTextFiles: [...jsonTextFiles],
  mapperPagingFiles: [...mapperPagingFiles],
  modules,
  symbols,
  // 「要自建什么」的清单：按用量倒序，前几名就是最该先做的通用件
  buildList: symbols.filter(s => s.count >= 3).map(s => s.symbol),
}

// ── 棘轮 ──
if (process.argv.includes('--check')) {
  const grows = []
  if (pomDependencies.includes('airpower-cookie')) {
    grows.push('已退场依赖不得直接引入：cn.hamm:airpower-cookie')
  }
  for (const dependency of directDependencies.filter(item => item.groupId === 'cn.hamm' && item.artifactId === 'airpower-curd')) {
    if (!dependency.exclusions.some(item => item.groupId === 'cn.hamm' && item.artifactId === 'airpower-cookie')) {
      grows.push('airpower-curd 必须显式排除已退场的传递依赖 cn.hamm:airpower-cookie')
    }
  }
  for (const { file, symbol } of retiredUsages) {
    grows.push(`已退场符号回潮（不允许用总量下降抵消）：${file} → ${symbol}`)
  }
  for (const { file, target } of wildcardUsages) {
    grows.push(`禁止 airpower 通配导入（不得隐藏未迁走的类型）：${file} → ${target}`)
  }
  for (const file of frameworkUsages) {
    grows.push(`自持 framework 不得再依赖 airpower：${file}`)
  }
  for (const file of independentUsages) {
    grows.push(`已独立编译的业务类型不得回潮 airpower：${file}`)
  }
  if (report.totals.airpowerFiles > BASELINE.files) {
    grows.push(`触及 airpower 的文件 ${report.totals.airpowerFiles} > 基线 ${BASELINE.files}`)
  }
  if (report.totals.symbols > BASELINE.symbols) {
    grows.push(`airpower 符号 ${report.totals.symbols} > 基线 ${BASELINE.symbols}`)
  }
  if (report.totals.references > BASELINE.references) {
    grows.push(`引用次数 ${report.totals.references} > 基线 ${BASELINE.references}`)
  }
  if (grows.length) {
    console.error('\n✗ 后端 airpower 退场门禁未通过：\n')
    for (const line of grows) {
      console.error(`  · ${line}`)
    }
    console.error('\n退场是长跑，数字只许降。若这轮确实新增了用法，说明该自建的通用件还没建完 ——')
    console.error('先看 `pnpm airpower:backend` 的「要自建什么」清单，把缺口补上再往下走。\n')
    process.exit(1)
  }
  console.log(
    `✓ 后端 airpower 棘轮通过：文件 ${report.totals.airpowerFiles}/${BASELINE.files} · `
    + `符号 ${report.totals.symbols}/${BASELINE.symbols} · 引用 ${report.totals.references}/${BASELINE.references}（只许降；不代表退场完成）`,
  )
  console.log('✓ 已退场符号零回潮 · 无 airpower 通配导入 · 自持 framework / 独立业务类型无 airpower 引用')
  process.exit(0)
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
  process.exit(0)
}

console.log(`\n🔍 后端 airpower 足迹（${report.totals.airpowerFiles} / ${report.totals.javaFiles} 个 Java 文件，${report.totals.references} 处引用）`)
console.log(`  口径：剥掉注释与 JavaDoc 后仍出现的真实引用；另有 ${report.totals.commentOnlyFiles} 个文件只在注释里提到它（迁移档案，不计）\n`)
console.log(`  父 POM：${report.parentPom ? 'cn.hamm:airpower（决定 Spring Boot 版本 —— 最硬的耦合）' : '无'}`)
console.log(`  依赖：${report.dependencies.join(', ') || '无'}`)
console.log(`  传递排除（POM 声明，非已解析依赖树）：${report.exclusions.map(item => `${item.from} → ${item.groupId}:${item.artifactId}`).join(', ') || '无'}`)
console.log(`  配置键：${report.configKeys.join(', ') || '无'}`)

console.log('\n── 按模块 ──────────────────────────────────────────────')
for (const { module, count, description } of modules) {
  console.log(`  ${module.padEnd(10)} ${String(count).padStart(4)} 处  ${description}`)
}

console.log('\n── 要自建 / 替换什么（按用量倒序，前 20）────────────────')
for (const { symbol, count, module } of symbols.slice(0, 20)) {
  console.log(`  ${String(count).padStart(4)}×  ${symbol.replace('cn.hamm.airpower.', '').padEnd(42)} [${module}]`)
}

console.log('\n── 用量 ≥3 的符号（= 通用件，先做这些收益最大）──────────')
console.log(`  ${report.buildList.length} 个：${report.buildList.map(s => s.replace('cn.hamm.airpower.', '')).join(', ')}`)
console.log('\n  用法：`pnpm airpower:backend -- --json` 给脚本读；`--check` 是 CI 棘轮（只许降）。\n')
