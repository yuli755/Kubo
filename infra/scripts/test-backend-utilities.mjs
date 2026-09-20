#!/usr/bin/env node
/**
 * 真实 Java 17 编译 + 独立工具契约，不需要 Maven/Redis/数据库。
 * 编译 framework/util、已迁走的真实业务枚举/常量和同一份契约，并用 javac AST 解析全部源码。
 * AST 只查语法及已知不可重复注解，不是依赖完整的类型检查；完整接线仍需 mvn compile/test。
 * 失败/缺少 JDK/零用例一律非零退出，不跳过，也不生成占位依赖来伪造编译通过。
 */
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { INDEPENDENT_DOMAIN_SOURCES } from './backend-independent-sources.mjs'

const root = path.resolve(import.meta.dirname, '..', '..')
const server = path.join(root, 'SPMS-Server')
const suffix = process.platform === 'win32' ? '.exe' : ''
const binary = name => process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', name + suffix) : name + suffix
const javac = binary('javac')
const java = binary('java')

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', timeout: 120_000, ...options })
  if (result.error || result.status !== 0) {
    throw new Error(`${command} 执行失败：${result.error?.message ?? result.stderr ?? result.status}`)
  }
  return `${result.stdout ?? ''}${result.stderr ?? ''}`
}

let output
try {
  const compilerVersion = run(javac, ['-version'])
  const runtimeVersion = run(java, ['-version'])
  if (!/javac 17(?:\.|\s)/.test(compilerVersion) || !/version "17(?:\.|"|\s)/.test(runtimeVersion)) {
    throw new Error('请用 JAVA_HOME 指向完整 JDK 17（项目 pom 的构建基线），不是 JRE 或其他 Java 版本')
  }
  const sourceDir = path.join(server, 'src/main/java/cn/hamm/spms/framework/util')
  const sources = fs.readdirSync(sourceDir).filter(file => file.endsWith('.java')).sort().map(file => path.join(sourceDir, file))
  if (sources.length === 0) {
    throw new Error('framework/util 没有生产源码，拒绝空转')
  }
  const utilityCount = sources.length
  sources.push(...INDEPENDENT_DOMAIN_SOURCES.map(file => path.join(root, file)))
  const productionCount = sources.length
  const contractsDir = path.join(server, 'src/test/java/cn/hamm/spms/framework/util')
  sources.push(...fs.readdirSync(contractsDir).filter(file => file.endsWith('Contracts.java')).sort().map(file => path.join(contractsDir, file)))
  sources.push(path.join(root, 'tools/java/JavaSourceCheck.java'))
  output = fs.mkdtempSync(path.join(os.tmpdir(), 'kubo-java-utilities-'))
  // javac @argfile 规避 Windows 命令行长度上限；路径内空格也必须保留。
  const argFile = path.join(output, 'sources.txt')
  fs.writeFileSync(argFile, sources.map(file => JSON.stringify(file.replaceAll('\\', '/'))).join('\n'))
  // 显式隔离 classpath/sourcepath，不能碰巧借用调用者 CLASSPATH 或隐式编译旧框架来“通过”。
  run(javac, ['--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-proc:none',
    '-classpath', output, '-sourcepath', output, '-d', output, `@${argFile}`])
  console.log(`✓ ${compilerVersion.trim()}：${productionCount} 个生产源码编译通过（${utilityCount} 个工具 + ${INDEPENDENT_DOMAIN_SOURCES.length} 个业务枚举/常量；无第三方 classpath）`)
  const syntax = run(java, ['-Dfile.encoding=UTF-8', '-Xmx256m', '-cp', output, 'cn.hamm.spms.tools.JavaSourceCheck',
    path.join(server, 'src/main/java'), path.join(server, 'src/test/java')])
  console.log(syntax.trim())
  const result = run(java, ['-Dfile.encoding=UTF-8', '-cp', output, 'cn.hamm.spms.framework.util.UtilityContracts'])
  console.log(result.trim())
  if (!/Utility contracts: [1-9]\d* passed/.test(result)) {
    throw new Error('工具契约没有完成或零用例，不能算通过')
  }
  const ascii = run(java, ['-Dfile.encoding=US-ASCII', '-cp', output, 'cn.hamm.spms.framework.util.UtilityContracts'])
  if (!/Utility contracts: [1-9]\d* passed/.test(ascii)) {
    throw new Error('非 UTF-8 默认字符集下的契约没有完成')
  }
  console.log('✓ 同一组契约在默认 US-ASCII 字符集下通过（CSV 必须显式 UTF-8，不依赖机器默认值）')
}
catch (error) {
  console.error(`✗ Java 工具契约失败：${error.message}`)
  process.exitCode = 1
}
finally {
  if (output) {
    fs.rmSync(output, { recursive: true, force: true })
  }
}
