#!/usr/bin/env node
/**
 * 源码文本完整性校验：编码污染（U+FFFD）与 Java 非法转义
 *
 * 两项检查放在一起，因为它们属于同一类问题：**机器一眼能看出来、但编译器和测试都可能放过去，
 * 最后只在生产页面上以"乱码"或"CI 红了但没人知道为什么"的形式显形**。
 *
 * ## 为什么需要它
 * 本仓曾有 5 个 Java 文件里 **105 行**中文被替换成 `U+FFFD`（``），且是提交进仓库的状态：
 * `git show` 分支起点与后续每个提交的计数完全一致，说明不是某一轮改动引入的，
 * 而是文件在某个环节被"解码失败 → 用替换符填空"写回了磁盘。
 *
 * 后果不只是注释难看：`UserController` 里 `Json.data(..., "查询成功")` 这类**返回给前端的提示**
 * 直接显示成乱码；`DevDataInitRunner` 会把坏名字（如房间 `"测试"`）写进 `menu`/业务表 ——
 * 一旦初始化过库，脏数据就长期留在里面，灰度验收时很容易被误判成"这次改造改坏了"。
 *
 * ## 为什么只卡 U+FFFD / 非字符，不做"mojibake 猜测"
 * `U+FFFD` 与 `U+FFFE`/`U+FFFF` 是**确定性事实**（合法源码里不该出现，出现即字节已丢），
 * 而"看起来像 `æ¸` 的编码错乱"很容易误伤正则表达式、多语言文案与测试夹具。
 * 门禁咬人一次，之后就没人信它了 —— 所以宁可窄一点。
 *
 * ## 第二项：Java 字面量完整性（非法转义 / 跨行未闭合 / 文本块配平）
 *
 * `text.matches("\*+")` 是对的；写成 `text.matches("\*+")` 少一个反斜杠就是
 * `illegal escape character`，javac 直接拒绝。本仓真实踩过：F5 起写的
 * `MaskedWriteGuard` 里就有这么一处，直到把 `arena/**` 加进 CI 触发分支、
 * 又修好 `mvnw` 缺执行位（exit 126）之后才暴露 —— 也就是说**这段代码从来没被编译过**，
 * 而我们连续几轮把"沙箱内测试全绿"当成了交付依据。
 * 与其等 CI 一个来回（下载依赖 + 编译 ≈ 3 分钟），不如在提交前用 20 毫秒自查。
 *
 * ## 为什么必须让机器来管这件事
 * 这类损坏不会导致编译失败、不会导致测试变红（测试断言的是行为，不是文案），
 * 只有人眼在特定页面才能看到 —— 正好是"人眼不该负责发现"的那一类问题。
 *
 * 用法：node infra/scripts/check-source-text.mjs [--verbose]
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import process from 'node:process'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')

/** 扫描范围：源码与文档，跳过依赖与产物（这些目录本身也不该被提交） */
const SCAN_ROOTS = ['src', 'packages', 'infra', 'SPMS-Server/src', 'SPMS-Server/docs', '.github']
/** 只检查文本文件；二进制（图片/字体/jar）里 U+FFFD 没有语义 */
const TEXT_EXT = new Set([
  '.ts', '.tsx', '.js', '.mjs', '.cjs', '.vue', '.java', '.md', '.json',
  '.yml', '.yaml', '.sql', '.xml', '.css', '.scss', '.html',
])
const SKIP_DIR = new Set(['node_modules', 'dist', 'target', '.git', 'coverage', '.venv'])

/**
 * 中日韩文字（含假名）：用于识别"引号紧贴汉字"这一坏味道
 */
const CJK = /[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/

const BAD = {
  '\uFFFD': 'U+FFFD 替换符（原始字节已丢失，无法自动还原）',
  '\uFFFE': 'U+FFFE 非字符',
  '\uFFFF': 'U+FFFF 非字符',
}

function walk(dir, out = []) {
  if (statSync(dir, { throwIfNoEntry: false })?.isDirectory() !== true) {
    return out
  }
  for (const name of readdirSync(dir)) {
    if (SKIP_DIR.has(name)) {
      continue
    }
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      walk(full, out)
    }
    else if (TEXT_EXT.has(name.slice(name.lastIndexOf('.')))) {
      out.push(full)
    }
  }
  return out
}

/**
 * Java 源码里字符串/字符字面量的完整性：非法转义 + 跨行未闭合 + 文本块配平
 *
 * 只在"引号内"判定，并跳过注释 —— 否则会误伤 `\*` 出现在注释或正则文档里的情况。
 * Java 的合法转义：\b \t \n \f \r \" \' \\ \0-\7（八进制）\uXXXX。
 *
 * 为什么不止查转义：`@DisplayName("说清"缺一个就整份拒"")` 这种"中文文案里用 ASCII 双引号"
 * 会把字面量提前关掉，javac 报的是 `<identifier> expected` 且行号经常落在下一行 ——
 * 本仓真实红过一次（FieldPolicyGapAnalyzerTest:44/56），而沙箱没有 JDK，人眼读三遍都很容易放过。
 * 同一台状态机顺带就能查它：走到换行时还"在引号里"即为此病。
 */
function javaLiteralFindings(file, text) {
  const out = []
  const hits = []
  let inString = false
  let inChar = false
  // Java 15+ 文本块（三个双引号）内部允许裸双引号与换行，不先识别它就会大面积误伤
  let inText = false
  let inLine = false
  let inBlock = false
  let line = 1
  let col = 0
  const push = (kind, why, at = line) => {
    out.push({
      kind,
      file,
      line: at,
      why,
      preview: (text.split('\n')[at - 1] ?? '').trim().slice(0, 90),
    })
  }
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    const next = text[i + 1]
    const after = text[i + 2]
    col++
    if (ch === '\n') {
      // javac 不允许字符串/字符字面量跨行：此刻还"在引号里"，说明前面某个双引号是文案里的裸引号
      if (inString || inChar) {
        push(inString ? 'java-string' : 'java-char', 'Java 字符串/字符常量跨行未闭合：多半是中文文案里用了 ASCII 双引号，应改用「」')
      }
      line++
      col = 0
      inLine = false
      inString = false
      inChar = false
      continue
    }
    if (inText) {
      if (ch === '\\') {
        i++
        continue
      }
      if (ch === '"' && next === '"' && after === '"') {
        inText = false
        i += 2
        col += 2
        continue
      }
      continue
    }
    if (inLine) {
      continue
    }
    if (inBlock) {
      if (ch === '*' && next === '/') {
        inBlock = false
        i++
      }
      continue
    }
    if (!inString && !inChar) {
      if (ch === '/' && next === '/') {
        inLine = true
        i++
        continue
      }
      if (ch === '/' && next === '*') {
        inBlock = true
        i++
        continue
      }
      if (ch === '"' && next === '"' && after === '"') {
        inText = true
        i += 2
        col += 2
        continue
      }
    }
    if (ch === '"' && !inChar) {
      if (inString) {
        // 闭引号：正常写法后面跟 ) , ; + . 或换行。紧跟汉字说明这个引号是中文文案里的裸引号，
        // 字面量在这里被提前关掉了 —— javac 的报错会是 "<identifier> expected"，行号还常落到下一行。
        if (CJK.test(text[i - 1] ?? '') && CJK.test(next ?? '')) {
          push('java-quote-adjacency', '中文字符串里出现了未转义的 ASCII 双引号（会把字面量提前关掉）：文案里的引用请改用「」')
        }
        inString = false
      }
      else {
        inString = true
      }
      continue
    }
    if (ch === "'" && !inString) {
      inChar = !inChar
      continue
    }
    if (!inString && !inChar) {
      continue
    }
    if (ch !== '\\') {
      continue
    }
    // 反斜杠：下一个字符必须是合法转义起始
    const ok = /[btnfr"'\\0-7ux]/.test(next ?? '')
    if (!ok) {
      hits.push({ line, snippet: text.slice(i - 6, i + 6).replace(/\n/g, ' ') })
    }
    i++ // 跳过被转义的字符
    col++
  }
  if (inText) {
    push('java-textblock', 'Java 文本块未闭合（起始的三个双引号没有配平）', line)
  }
  for (const hit of hits) {
    out.push({
      kind: 'java-escape',
      file,
      line: hit.line,
      why: 'Java 非法转义：反斜杠后必须是 b t n f r 引号 反斜杠 八进制数字 或 u',
      preview: hit.snippet,
    })
  }
  return out
}

/**
 * 第三项：用了 JDK 类型却没 import
 *
 * 触发它的是真实一次 CI 红：行级盘点给 `DataPolicyService` 加了个返回 `List<DataPolicyEntity>`
 * 的方法，而这个类原先只用到 `Set`/`LinkedHashSet`，没有 `java.util.List` 的 import。
 * 沙箱里没有 JDK（`repo1.maven.org` 也不通），javac 是唯一能判这件事的裁判，而 CI 的**日志正文**
 * 存在 blob 存储上、本沙箱连不上（`SSL_ERROR_SYSCALL`），只有 annotation 通道能拿到
 * `cannot find symbol`。也就是说：**这一类错误在本环境里没有任何提交前自查手段，只能烧一个 CI 来回。**
 *
 * 所以这里用 30 行把"最常见的一类 cannot find symbol"提前判掉：只盯 JDK 类型名（项目自己不会
 * 声明一个叫 `List` 的类型，盯错了也只会指向真实问题），且必须满足"文件里出现过 = 需要 import 过"。
 *
 * **误伤测试是免费的**：仓库里这 410 个 Java 文件是 CI 编译通过的，因此本项在它们上面必须 0 命中；
 * 一旦命中，先怀疑扫描器而不是怀疑代码（这是本仓门禁的老规矩：门禁咬错了就先收窄判据）。
 */
const JDK_TYPES = new Map([
  'List', 'ArrayList', 'LinkedList', 'Set', 'HashSet', 'LinkedHashSet', 'TreeSet', 'EnumSet',
  'SortedSet', 'NavigableSet', 'Map', 'HashMap', 'LinkedHashMap', 'TreeMap', 'EnumMap',
  'SortedMap', 'NavigableMap', 'Collections', 'Arrays', 'Optional', 'OptionalInt', 'OptionalLong',
  'Iterator', 'Objects', 'Random', 'UUID', 'Base64', 'Scanner', 'Formatter',
  'Locale', 'Date', 'Calendar', 'Properties', 'Stack', 'Queue', 'Deque', 'ArrayDeque',
  'PriorityQueue', 'Comparator', 'BitSet', 'ServiceLoader', 'IllegalFormatConversionException',
].map(name => [name, 'java.util']))

for (const name of ['Stream', 'Collectors', 'IntStream', 'LongStream', 'DoubleStream', 'StreamSupport']) {
  JDK_TYPES.set(name, 'java.util.stream')
}
for (const name of ['Function', 'Consumer', 'BiFunction', 'Supplier', 'Predicate', 'UnaryOperator', 'BinaryOperator', 'BiConsumer']) {
  JDK_TYPES.set(name, 'java.util.function')
}
for (const name of ['ConcurrentHashMap', 'ConcurrentMap', 'CopyOnWriteArrayList', 'TimeUnit', 'Executors', 'ExecutorService', 'ScheduledExecutorService', 'CompletableFuture', 'CountDownLatch', 'Semaphore', 'ThreadFactory', 'ThreadPoolExecutor']) {
  JDK_TYPES.set(name, 'java.util.concurrent')
}
for (const name of ['AtomicBoolean', 'AtomicInteger', 'AtomicLong', 'AtomicReference']) {
  JDK_TYPES.set(name, 'java.util.concurrent.atomic')
}
for (const name of ['LocalDate', 'LocalDateTime', 'LocalTime', 'ZoneId', 'ZoneOffset', 'ZonedDateTime', 'Duration', 'Instant', 'Year', 'YearMonth', 'Month', 'DateTimeFormatter']) {
  JDK_TYPES.set(name, 'java.time')
}
// Iterable 属于 java.lang，无需 import；DateTimeFormatter 实际位于 java.time.format。
JDK_TYPES.set('DateTimeFormatter', 'java.time.format')

for (const name of ['File', 'IOException', 'UncheckedIOException', 'Serializable', 'FileNotFoundException', 'BufferedReader', 'InputStream', 'OutputStream', 'ByteArrayOutputStream', 'PrintWriter', 'Closeable', 'Reader', 'Writer']) {
  JDK_TYPES.set(name, 'java.io')
}
for (const name of ['BigDecimal', 'BigInteger', 'RoundingMode', 'MathContext']) {
  JDK_TYPES.set(name, 'java.math')
}
for (const name of ['StandardCharsets', 'Charset', 'CharacterCodingException']) {
  JDK_TYPES.set(name, 'java.nio.charset')
}
for (const name of ['SimpleDateFormat', 'ParseException', 'MessageFormat', 'DecimalFormat']) {
  JDK_TYPES.set(name, 'java.text')
}
for (const name of ['URI', 'URL', 'URLEncoder', 'URLDecoder']) {
  JDK_TYPES.set(name, 'java.net')
}

/**
 * 把注释与字面量挖空（换行保留），只留"真正的代码"
 *
 * 必须挖空：javadoc 里写 `@return 一个 List` 或日志串里写 "Set" 都不需要 import，
 * 拿原文扫会产生大片误伤。状态机与上面 `javaLiteralFindings` 一致（文本块/行注释/块注释/串/字符）。
 */
function scrubJavaCode(text) {
  const buf = []
  let inString = false
  let inChar = false
  let inText = false
  let inLine = false
  let inBlock = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    const next = text[i + 1]
    const after = text[i + 2]
    if (ch === '\n') {
      buf.push('\n')
      inLine = false
      inString = false
      inChar = false
      continue
    }
    buf.push(inText || inLine || inBlock || inString || inChar ? ' ' : ch)
    if (inText) {
      if (ch === '\\') {
        buf.push(' ')
        i++
      }
      else if (ch === '"' && next === '"' && after === '"') {
        inText = false
        buf.push('  ')
        i += 2
      }
      continue
    }
    if (inLine || inBlock) {
      if (inBlock && ch === '*' && next === '/') {
        buf.push(' ')
        i++
        inBlock = false
      }
      continue
    }
    if (ch === '/' && next === '/') {
      inLine = true
      continue
    }
    if (ch === '/' && next === '*') {
      inBlock = true
      continue
    }
    if (ch === '"' && next === '"' && after === '"') {
      inText = true
      buf.push('  ')
      i += 2
      continue
    }
    if (ch === '"' && !inChar) {
      inString = !inString
      continue
    }
    if (ch === "'" && !inString) {
      inChar = !inChar
      continue
    }
    if ((ch === '"' || ch === "'") && (inString || inChar)) {
      continue
    }
    if (ch === '\\') {
      buf.push(' ')
      i++
    }
  }
  return buf.join('')
}

function javaImportFindings(file, text) {
  const out = []
  if (file.endsWith('module-info.java')) {
    return out
  }
  const code = scrubJavaCode(text)
  const lines = code.split('\n')
  const importedSimpleNames = new Set()
  let hasStarImport = false
  // import 行**置空而不是删掉**：删掉会让行号整体前移，报出来的位置落到上一行，
  // 定位一次误伤能白耗半小时（自检抓到过这个病）。
  const body = lines.map((line) => {
    if (!/^[ \t]*import\b/.test(line)) {
      return line
    }
    const star = /^[ \t]*import[ \t]+(?:static[ \t]+)?([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\.[ \t]*\*[ \t]*;/.exec(line)
    if (star) {
      // 非静态通配 import 仍保守处理（jakarta 也有 Predicate）。
      // static Enum.* 只引入枚举成员，不能让缺少 java.util.List 的整份文件逃过检查。
      // 如确需同名的静态嵌套类型，显式 import 那个类型，不用通配写法掩盖归属。
      if (!/^[ \t]*import[ \t]+static[ \t]+/.test(line)) {
        hasStarImport = true
      }
      return ''
    }
    const one = /^[ \t]*import[ \t]+(?:static[ \t]+)?([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)[ \t]*;/.exec(line)
    if (one) {
      importedSimpleNames.add(one[1].slice(one[1].lastIndexOf('.') + 1))
    }
    return ''
  })
  if (hasStarImport) {
    return out
  }
  const bodyText = body.join('\n')
  // 文件自己声明了同名类型（interface List {}）时不该报 —— 那是它自己的类型，不需要 import
  const declaredHere = new Set([...bodyText.matchAll(/\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)/g)].map(m => m[1]))
  for (const [name, pkg] of JDK_TYPES) {
    if (importedSimpleNames.has(name) || declaredHere.has(name)) {
      continue
    }
    // java.util.List 这种全限定写法不需要 import：前面紧跟点号或 $ 的出现全部跳过
    const use = new RegExp(`(?<![.$\\w])${name}(?![\\w$])`)
    const at = bodyText.search(use)
    if (at < 0) {
      continue
    }
    const line = bodyText.slice(0, at).split('\n').length
    out.push({
      kind: 'java-jdk-import',
      file,
      line,
      why: `用了 ${name} 却没有 import（javac 的 cannot find symbol；JDK 里它在 ${pkg}）`,
      // 预览取原文而不是挖空后的代码：串被挖掉后只剩一行引号，看不出问题在哪
      preview: (text.split('\n')[line - 1] ?? '').trim().slice(0, 90),
    })
  }
  return out
}

/** Java 文件的三项检查合流（自检与主流程共用同一入口，避免两边行为漂移） */
function javaFindings(file, text) {
  return [...javaLiteralFindings(file, text), ...javaImportFindings(file, text)]
}

let selfChecked = 0

const verbose = process.argv.includes('--verbose')
const findings = []
let scanned = 0
let javaScanned = 0

for (const root of SCAN_ROOTS) {
  for (const file of walk(resolve(ROOT, root))) {
    scanned++
    const text = readFileSync(file, 'utf-8')
    if (file.endsWith('.java')) {
      javaScanned++
      findings.push(...javaFindings(relative(ROOT, file), text))
    }
    if (!/[\uFFFD\uFFFE\uFFFF]/.test(text)) {
      continue
    }
    text.split('\n').forEach((line, index) => {
      for (const [char, why] of Object.entries(BAD)) {
        if (!line.includes(char)) {
          continue
        }
        findings.push({
          file: relative(ROOT, file),
          line: index + 1,
          why,
          preview: line.replace(/[\uFFFD\uFFFE\uFFFF]/g, '□').trim().slice(0, 90),
        })
        break
      }
    })
  }
}

/**
 * 门禁自检：证明这几项检查各自真的能咬人，且不误伤合法写法
 *
 * 为什么把自检内嵌进脚本而不是另写测试文件：这个脚本由 CI 每轮调用，自检便随之执行。
 * 若它哪天退化成"永远返回空"的摆设（锚点写歪、状态机漏分支），CI 会立刻红 ——
 * 而不是等到有人再次踩坑、才发现门禁早已失灵好几个月。
 */
function selfCheck() {
  const problems = []
  globalThis.__cases = 0
  const cases = [
    {
      name: '中文文案里的裸 ASCII 双引号（本次 CI 真实红过的形状）',
      java: 'class A {\n  void a() { b("只配了遮蔽 → 说清"缺一个就整份被拒""); }\n}\n',
      expect: 'java-quote-adjacency',
    },
    {
      name: '中文串里奇数个 ASCII 引号（字面量跨行未闭合）',
      java: 'class A {\n  void a() { b("说清"缺一个就整份被拒"); }\n}\n',
      expect: 'java-string',
    },
    {
      name: '正则少写一个反斜杠',
      java: 'class A {\n  void a() { b(text.matches("\\*+")); }\n}\n',
      expect: 'java-escape',
    },
    {
      name: '文本块里的裸双引号与跨行（合法，必须不报）',
      java: 'class A {\n  String s = """\n    允许 " 引号 " 跨行\n    """;\n}\n',
      expect: null,
    },
    {
      name: '转义引号 + 中文书名号（合法，必须不报）',
      java: 'class A {\n  void a() { b("他说\\"好\\"，即「好」"); }\n}\n',
      expect: null,
    },
    {
      name: '用了 List / ArrayList 却没 import（2026-09-07 CI 编译失败那一行的形状）',
      java: 'package p;\n\nclass A {\n  List<String> l = new ArrayList<>();\n}\n',
      expect: 'java-jdk-import',
    },
    {
      name: '同包类型 / 通配 import / 全限定写法（都不该报）',
      java: 'package p;\nimport java.time.*;\nimport java.io.IOException;\n\nclass A {\n  void a() { java.util.List<String> l = null; LocalTime t = null; try { f(); } catch (IOException e) { } }\n}\n',
      expect: null,
    },
    {
      name: 'javadoc 与日志串里出现 List/Set（注释不算使用，不该报）',
      java: 'package p;\n\n/** @return 一个 List，别学 Set */\nclass A {\n  void a() { log.info("Set/List 都在串里 Map"); }\n}\n',
      expect: null,
    },
    {
      name: 'java.lang.Iterable 不需要 import（真实 javac 17 已验证）',
      java: 'class A { Iterable<String> values; }\n',
      expect: null,
    },
    {
      name: '静态枚举通配不能掩盖缺少 List import（B2a CodeRuleService 漏项）',
      java: 'package p;\nimport static p.CodeRuleParam.*;\nclass A { List<String> fields; }\n',
      expect: 'java-jdk-import',
    },
    {
      name: '静态方法通配不能掩盖缺少 Map import',
      java: 'package p;\nimport static java.util.Objects.*;\nclass A { Map<String, String> values; }\n',
      expect: 'java-jdk-import',
    },
    {
      name: '静态枚举通配加显式 List import（合法）',
      java: 'package p;\nimport static p.CodeRuleParam.*;\nimport java.util.List;\nclass A { List<String> fields; }\n',
      expect: null,
    },
    {
      name: '静态通配 import 那行本身不该被当成使用 Objects',
      java: 'package p;\nimport static java.util.Objects.*;\n\nclass A {\n  void a() { }\n}\n',
      expect: null,
    },
  ]
  for (const item of cases) {
    selfChecked++
    const kinds = new Set(javaFindings('<self-test>', item.java).map((found) => found.kind))
    if (item.expect === null) {
      if (kinds.size > 0) {
        problems.push(`自检：「${item.name}」被误判为 ${[...kinds].join('/')}`)
      }
      continue
    }
    if (!kinds.has(item.expect)) {
      problems.push(`自检：「${item.name}」没有被检出（期望 ${item.expect}）`)
    }
  }
  return problems
}

const selfProblems = selfCheck()
if (selfProblems.length > 0) {
  console.error('\n✗ check-source-text 自检失败：门禁本身不可信，先修门禁再谈业务代码')
  for (const problem of selfProblems) {
    console.error(`  ${problem}`)
  }
  process.exit(1)
}

if (findings.length === 0) {
  console.log(`✓ 文本完整性：${scanned} 个文件无 U+FFFD / 非字符；${javaScanned} 个 Java 文件的字面量、转义与 JDK import 已扫描；门禁自检 ${selfChecked} 例通过`)
  process.exit(0)
}

const byFile = new Map()
for (const item of findings) {
  if (!byFile.has(item.file)) {
    byFile.set(item.file, [])
  }
  byFile.get(item.file).push(item)
}

console.error(`\n✗ 发现 ${findings.length} 处源码文本问题，分布在 ${byFile.size} 个文件：`)
for (const [file, items] of byFile) {
  console.error(`\n  ${file}（${items.length} 行）`)
  const shown = verbose ? items : items.slice(0, 5)
  for (const item of shown) {
    console.error(`    ${String(item.line).padStart(5)}: ${item.preview}  [${item.why}]`)
  }
  if (!verbose && items.length > shown.length) {
    console.error(`    … 还有 ${items.length - shown.length} 行，加 --verbose 查看全部`)
  }
}
console.error(`\n  首条说明：${findings[0].why}。`)
const kinds = new Set(findings.map((item) => item.kind))
if (kinds.has('encoding')) {
  console.error('  编码类处理：按上下文重写这些中文（原字节已丢，机器无法还原），')
  console.error('  并检查产生损坏的环节（脚本读写未指定 UTF-8、记事本另存、制品回传等）。')
}
if (kinds.has('java-string') || kinds.has('java-char') || kinds.has('java-textblock') || kinds.has('java-quote-adjacency')) {
  console.error('  字面量类处理：Java 字符串不能跨行。中文文案里引用词语时用「」，')
  console.error('  别用 ASCII 双引号 —— 它会提前关掉字面量，而 javac 报的行号往往落在下一两行，人眼最难定位。')
}
if (kinds.has('java-jdk-import')) {
  console.error('  import 类处理：补上对应 import（或在 CI annotation 里确认 cannot find symbol 已消失）。')
  console.error('  若这是误伤（项目里真有一个同名的自有类型），把该名从 JDK_TYPES 移除并在此说明理由，别放宽整条规则。')
}
if (kinds.has('java-escape')) {
  console.error('  转义类处理：Java 源码里的正则要双写反斜杠（matches 的参数是正则，不是字面量）。')
  console.error('  并确认后端 CI 的 Compile 步骤真的执行过：沙箱内无法运行 mvn，看起来对 != 编译得过。')
}
process.exit(1)
