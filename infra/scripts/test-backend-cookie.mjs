#!/usr/bin/env node
/**
 * 真实 Spring/Boot/Servlet Cookie 切片；不包含 AirPower，不启动全应用或数据库。
 * 依赖必须匹配已核验发布字节，默认 Maven 本地库，或 KUBO_COOKIE_JARS 离线平铺目录。
 */
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { COOKIE_TEST_ARTIFACTS, COOKIE_TEST_BOOT_VERSION } from './backend-cookie-artifacts.mjs'

const root = path.resolve(import.meta.dirname, '..', '..')
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
  if (pom.match(/<springboot\.version>([^<]+)<\/springboot\.version>/)?.[1]?.trim() !== COOKIE_TEST_BOOT_VERSION)
    throw new Error('Spring Boot 基线变化，请重新核对 Cookie 切片正式依赖')
  output = fs.mkdtempSync(path.join(os.tmpdir(), 'kubo-cookie-contracts-'))
  fs.mkdirSync(path.join(output, 'deps'))
  const jars = COOKIE_TEST_ARTIFACTS.map((artifact) => {
    const name = `${artifact.artifact}-${artifact.version}.jar`
    const source = process.env.KUBO_COOKIE_JARS
      ? path.join(process.env.KUBO_COOKIE_JARS, name)
      : path.join(process.env.MAVEN_REPO_LOCAL ?? path.join(os.homedir(), '.m2/repository'), artifact.group, artifact.artifact, artifact.version, name)
    if (!fs.existsSync(source))
      throw new Error(`缺少正式 Cookie 依赖 ${name}；先解析 Maven 依赖或指定 KUBO_COOKIE_JARS`)
    const bytes = fs.readFileSync(source)
    if (createHash('sha256').update(bytes).digest('hex') !== artifact.sha256)
      throw new Error(`${name} 校验和不符，拒绝执行`)
    const verified = path.join(output, 'deps', name)
    fs.writeFileSync(verified, bytes)
    return { path: verified, artifact: artifact.artifact }
  })
  const compiler = run(binary('javac'), ['-version'])
  const runtime = run(binary('java'), ['-version'])
  if (!/javac 17(?:\.|\s)/.test(compiler) || !/version "17(?:\.|"|\s)/.test(runtime))
    throw new Error('需要完整 JDK 17')
  const lombok = jars.find(jar => jar.artifact === 'lombok').path
  const sources = [
    'SPMS-Server/src/main/java/cn/hamm/spms/common/cookie/CookieConfig.java',
    'SPMS-Server/src/main/java/cn/hamm/spms/common/cookie/CookieHelper.java',
    'SPMS-Server/src/test/java/cn/hamm/spms/common/cookie/CookieContracts.java',
  ]
  const args = path.join(output, 'sources.txt')
  fs.writeFileSync(args, sources.map(file => JSON.stringify(path.join(root, file).replaceAll('\\', '/'))).join('\n'))
  // Spring/Servlet 的运行时注解无需编译期 processor 认领；只关闭此类 processing 提示，其余 lint 仍按错误处理。
  run(binary('javac'), ['--release', '17', '-encoding', 'UTF-8', '-Xlint:all,-processing', '-Werror',
    '-classpath', jars.map(jar => jar.path).join(path.delimiter), '-sourcepath', output,
    '-processorpath', lombok, '-processor', 'lombok.launch.AnnotationProcessorHider$AnnotationProcessor',
    '-d', output, `@${args}`])
  console.log(`✓ ${compiler.trim()}：2 个真实生产组件编译通过（正式 Spring/Boot/Servlet，无 AirPower）`)
  const classpath = [output, ...jars.filter(jar => jar.artifact !== 'lombok').map(jar => jar.path)].join(path.delimiter)
  for (const encoding of ['UTF-8', 'US-ASCII']) {
    const result = run(binary('java'), [`-Dfile.encoding=${encoding}`, '-cp', classpath, 'cn.hamm.spms.common.cookie.CookieContracts'])
    if (!/Cookie contracts: [1-9]\d* passed/.test(result))
      throw new Error('Cookie 契约未完成或零用例')
    console.log(encoding === 'UTF-8' ? result.trim() : '✓ Cookie 契约在默认 US-ASCII 下通过')
  }
}
catch (error) {
  console.error(`✗ Cookie 契约失败：${error.message}`)
  process.exitCode = 1
}
finally {
  if (output)
    fs.rmSync(output, { recursive: true, force: true })
}
