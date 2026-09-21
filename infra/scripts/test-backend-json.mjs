#!/usr/bin/env node
/**
 * 用已核验的正式 Jackson/Lombok 编译真实 JSON codec、事件 DTO、审计快照并运行共享契约。
 * 不编造第三方占位类；缺失/版本不符/编译失败/零用例均失败。不是完整 Maven/Redis/HTTP 验收。
 * 默认读取 ~/.m2/repository；离线包可用 KUBO_JSON_JARS 指定仅含对应版本 JAR 的平铺目录。
 */
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { JSON_TEST_ARTIFACTS, JSON_TEST_BOOT_VERSION } from './backend-json-artifacts.mjs'

const root = path.resolve(import.meta.dirname, '..', '..')
const main = path.join(root, 'SPMS-Server/src/main/java/cn/hamm/spms')
const test = path.join(root, 'SPMS-Server/src/test/java/cn/hamm/spms/framework/json')
const suffix = process.platform === 'win32' ? '.exe' : ''
const binary = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + suffix) : name + suffix

function run(command, args) {
  const result = spawnSync(command, args, { encoding: 'utf8', timeout: 120_000 })
  if (result.error || result.status !== 0)
    throw new Error(`${command} 失败：${result.error?.message ?? `${result.stdout}\n${result.stderr}`}`)
  return `${result.stdout ?? ''}${result.stderr ?? ''}`
}

let output
try {
  const pom = fs.readFileSync(path.join(root, 'SPMS-Server/pom.xml'), 'utf8').replace(/<!--[\s\S]*?-->/g, '')
  const bootVersion = pom.match(/<springboot\.version>([^<]+)<\/springboot\.version>/)?.[1]?.trim()
  if (bootVersion !== JSON_TEST_BOOT_VERSION)
    throw new Error('Spring Boot BOM 基线已变化，请重新核对 JSON 兼容协议与正式依赖校验和')
  output = fs.mkdtempSync(path.join(os.tmpdir(), 'kubo-json-contracts-'))
  fs.mkdirSync(path.join(output, 'deps'))
  const jars = JSON_TEST_ARTIFACTS.map((artifact) => {
    const file = `${artifact.artifact}-${artifact.version}.jar`
    const location = process.env.KUBO_JSON_JARS
      ? path.join(process.env.KUBO_JSON_JARS, file)
      : path.join(process.env.MAVEN_REPO_LOCAL ?? path.join(os.homedir(), '.m2/repository'), artifact.group, artifact.artifact, artifact.version, file)
    if (!fs.existsSync(location))
      throw new Error(`缺少正式依赖 ${file}；先用 Maven 解析依赖，或设置 KUBO_JSON_JARS 指向已校验的离线目录`)
    const bytes = fs.readFileSync(location)
    const sha256 = createHash('sha256').update(bytes).digest('hex')
    if (sha256 !== artifact.sha256)
      throw new Error(`${file} 校验和不符，拒绝作为测试依赖执行`)
    // 运行刚核验的字节副本，避免原缓存文件在核验后被并行下载/改写。
    const verified = path.join(output, 'deps', file)
    fs.writeFileSync(verified, bytes)
    return verified
  })
  const compiler = run(binary('javac'), ['-version'])
  const runtime = run(binary('java'), ['-version'])
  if (!/javac 17(?:\.|\s)/.test(compiler) || !/version "17(?:\.|"|\s)/.test(runtime))
    throw new Error('需要完整 JDK 17（与项目 pom 一致）')
  const jackson = jars.slice(0, 3)
  const lombok = jars[3]
  function compile(sources, processing) {
    const args = path.join(output, processing ? 'dto-sources.txt' : 'codec-sources.txt')
    fs.writeFileSync(args, sources.map(file => JSON.stringify(file.replaceAll('\\', '/'))).join('\n'))
    const processor = processing ? ['-processorpath', lombok, '-processor', 'lombok.launch.AnnotationProcessorHider$AnnotationProcessor'] : ['-proc:none']
    run(binary('javac'), ['--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
      '-classpath', [output, ...jackson, ...(processing ? [lombok] : [])].join(path.delimiter),
      '-sourcepath', output, ...processor, '-d', output, `@${args}`])
  }
  // 必须编译生产 DTO，不能把手写的 DTO stub 当成应用接线证据。
  compile([
    path.join(main, 'module/system/datapermission/event/DataPermissionChangedEvent.java'),
    path.join(main, 'module/system/datapermission/enums/DataPermissionAction.java'),
  ], true)
  compile([
    path.join(main, 'framework/json/JsonCodec.java'),
    path.join(main, 'framework/json/JsonCodecException.java'),
    path.join(main, 'module/system/datapermission/service/OperationLogSnapshot.java'),
    path.join(test, 'JsonContracts.java'),
  ], false)
  console.log(`✓ ${compiler.trim()}：5 个生产源码 + 正式 Jackson 2.18.3 / Lombok 1.18.36 编译通过`)
  for (const encoding of ['UTF-8', 'US-ASCII']) {
    const result = run(binary('java'), [`-Dfile.encoding=${encoding}`, '-cp', [output, ...jackson].join(path.delimiter), 'cn.hamm.spms.framework.json.JsonContracts'])
    if (!/JSON contracts: [1-9]\d* passed/.test(result))
      throw new Error('JSON 契约未完成或零用例')
    console.log(encoding === 'UTF-8' ? result.trim() : '✓ 同一组 JSON 契约在默认 US-ASCII 下通过，Redis 文本显式 UTF-8')
  }
}
catch (error) {
  console.error(`✗ JSON 契约失败：${error.message}`)
  process.exitCode = 1
}
finally {
  if (output)
    fs.rmSync(output, { recursive: true, force: true })
}
