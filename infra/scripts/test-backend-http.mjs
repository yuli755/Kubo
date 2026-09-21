#!/usr/bin/env node
/**
 * HTTP 错误契约：真实 Spring MVC + 项目生产错误码/异常/advice/响应桥。
 * 默认使用 Maven 的正式 legacy core JAR；仅显式指定 KUBO_HTTP_LEGACY_CORE_SOURCES 时允许
 * 逐文件校验并编译官方固定源码作兼容参考。后者不是 Maven 依赖解析或全应用验收。
 * 不下载依赖，不生成框架 stub；缺包/错 hash/零用例均失败。
 */
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { HTTP_TEST_ARTIFACTS, LEGACY_CORE_SHA1 } from './backend-http-artifacts.mjs'

const root = path.resolve(import.meta.dirname, '..', '..')
const suffix = process.platform === 'win32' ? '.exe' : ''
const binary = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + suffix) : name + suffix
const repo = process.env.MAVEN_REPO_LOCAL ?? path.join(os.homedir(), '.m2/repository')
function run(command, args) {
  const result = spawnSync(command, args, { encoding: 'utf8', timeout: 120_000 })
  if (result.error || result.status !== 0)
    throw new Error(`${command} 失败：${result.error?.message ?? `${result.stdout}\n${result.stderr}`}`)
  return `${result.stdout ?? ''}${result.stderr ?? ''}`
}
let output
try {
  const pom = fs.readFileSync(path.join(root, 'SPMS-Server/pom.xml'), 'utf8').replace(/<!--[\s\S]*?-->/g, '')
  if (pom.match(/<springboot\.version>([^<]+)<\/springboot\.version>/)?.[1]?.trim() !== '3.4.4'
    || pom.match(/<airpower\.version>([^<]+)<\/airpower\.version>/)?.[1]?.trim() !== '6.4.3')
    throw new Error('BOM/旧框架基线已变更，请更新 HTTP 兼容契约和依赖核验')
  output = fs.mkdtempSync(path.join(os.tmpdir(), 'kubo-http-contracts-'))
  fs.mkdirSync(path.join(output, 'deps'))
  const jars = HTTP_TEST_ARTIFACTS.map((artifact) => {
    const name = `${artifact.artifact}-${artifact.version}.jar`
    const source = process.env.KUBO_HTTP_JARS
      ? path.join(process.env.KUBO_HTTP_JARS, name)
      : path.join(repo, artifact.group, artifact.artifact, artifact.version, name)
    if (!fs.existsSync(source))
      throw new Error(`缺少正式 HTTP 依赖 ${name}；先解析 Maven 依赖或设置 KUBO_HTTP_JARS`)
    const bytes = fs.readFileSync(source)
    if (createHash('sha256').update(bytes).digest('hex') !== artifact.sha256)
      throw new Error(`${name} 校验和不符，拒绝执行`)
    const verified = path.join(output, 'deps', name)
    fs.writeFileSync(verified, bytes)
    return { path: verified, artifact: artifact.artifact }
  })
  const legacy = path.join(output, 'legacy')
  fs.mkdirSync(legacy)
  let sources = null
  let legacyClasspath
  if (process.env.KUBO_HTTP_LEGACY_CORE_SOURCES) {
    const manifest = JSON.parse(fs.readFileSync(path.join(root, 'infra/scripts/backend-http-legacy-source.json'), 'utf8'))
    if (manifest.version !== '6.3.1' || manifest.ref !== 'fe1691b2c978' || manifest.files.length !== 41)
      throw new Error('旧 core 源码基线不完整，拒绝空转或替换测试参照')
    sources = manifest.files.map((file) => {
      if (!/^cn\/hamm\/airpower\/core\/[\w/]+\.java$/.test(file.path) || !/^[a-f0-9]{64}$/.test(file.sha256))
        throw new Error('上游源码清单包含非法路径或摘要')
      const bytes = fs.readFileSync(path.join(process.env.KUBO_HTTP_LEGACY_CORE_SOURCES, file.path))
      if (createHash('sha256').update(bytes).digest('hex') !== file.sha256)
        throw new Error(`官方固定源码校验失败：${file.path}`)
      const verified = path.join(legacy, 'source', file.path)
      fs.mkdirSync(path.dirname(verified), { recursive: true })
      fs.writeFileSync(verified, bytes)
      return verified
    })
    legacyClasspath = path.join(legacy, 'classes')
    fs.mkdirSync(legacyClasspath)
  }
  else {
    const source = process.env.KUBO_HTTP_LEGACY_CORE_JAR ?? path.join(repo, 'cn/hamm/airpower-core/6.3.1/airpower-core-6.3.1.jar')
    if (!fs.existsSync(source))
      throw new Error('缺少正式 airpower-core 6.3.1；源码参考只能显式指定 KUBO_HTTP_LEGACY_CORE_SOURCES，不能静默降级')
    const bytes = fs.readFileSync(source)
    if (createHash('sha1').update(bytes).digest('hex') !== LEGACY_CORE_SHA1)
      throw new Error('airpower-core 6.3.1 正式 JAR 校验失败')
    legacyClasspath = path.join(legacy, 'airpower-core-6.3.1.jar')
    fs.writeFileSync(legacyClasspath, bytes)
  }
  const compiler = run(binary('javac'), ['-version'])
  const runtime = run(binary('java'), ['-version'])
  if (!/javac 17(?:\.|\s)/.test(compiler) || !/version "17(?:\.|"|\s)/.test(runtime))
    throw new Error('HTTP 契约需要完整 JDK 17')
  const deps = jars.map(jar => jar.path).join(path.delimiter)
  const lombok = jars.find(jar => jar.artifact === 'lombok').path
  function argFile(name, paths) {
    const target = path.join(output, name)
    fs.writeFileSync(target, paths.map(file => JSON.stringify(file.replaceAll('\\', '/'))).join('\n'))
    return `@${target}`
  }
  if (sources) {
    // 不修改上游源码来消警告；只对项目新代码启用 -Werror，上游诊断如实输出。
    const log = run(binary('javac'), ['--release', '17', '-encoding', 'UTF-8', '-cp', deps, '-sourcepath', legacyClasspath,
      '-processorpath', lombok, '-processor', 'lombok.launch.AnnotationProcessorHider$AnnotationProcessor',
      '-d', legacyClasspath, argFile('legacy.txt', sources)])
    console.log('✓ 逐文件核验并编译上游固定 core 源码（41 文件；源码参考模式，不是正式 JAR/Maven 验收）')
    if (log.trim())
      console.log(log.trim())
  }
  else {
    console.log('✓ 使用核验过的正式 airpower-core 6.3.1 JAR 作为兼容参考')
  }
  const production = [
    'framework/error/BusinessException.java', 'framework/error/ErrorCode.java', 'framework/http/ApiResponse.java',
    'common/AppConstant.java', 'common/exception/Errors.java', 'common/exception/CustomError.java',
    'common/exception/BusinessExceptionAdvice.java', 'common/exception/DataIntegrityViolationAdvice.java',
    'common/compat/LegacyApiResponseBridge.java',
  ].map(file => path.join(root, 'SPMS-Server/src/main/java/cn/hamm/spms', file))
  const test = path.join(root, 'SPMS-Server/src/test/java/cn/hamm/spms/framework/http/HttpErrorContracts.java')
  const classes = path.join(output, 'classes')
  fs.mkdirSync(classes)
  run(binary('javac'), ['--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-proc:none',
    '-classpath', [legacyClasspath, deps].join(path.delimiter), '-sourcepath', classes,
    '-d', classes, argFile('project.txt', [...production, test])])
  console.log(`✓ ${compiler.trim()}：9 个项目生产源码真实编译（Spring MVC + 明示旧响应桥）`)
  const classpath = [classes, legacyClasspath, deps].join(path.delimiter)
  for (const encoding of ['UTF-8', 'US-ASCII']) {
    const result = run(binary('java'), [`-Dfile.encoding=${encoding}`, '-cp', classpath, 'cn.hamm.spms.framework.http.HttpErrorContracts'])
    if (!/HTTP error contracts: [1-9]\d* passed/.test(result))
      throw new Error('HTTP 契约未完成或零用例')
    console.log(encoding === 'UTF-8' ? result.trim() : '✓ HTTP 契约在默认 US-ASCII 下通过')
  }
}
catch (error) {
  console.error(`✗ HTTP 契约失败：${error.message}`)
  process.exitCode = 1
}
finally {
  if (output)
    fs.rmSync(output, { recursive: true, force: true })
}
