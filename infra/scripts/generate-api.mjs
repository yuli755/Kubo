#!/usr/bin/env node
/**
 * API 文档类型生成脚本
 * 从后端 OpenAPI 文档生成 TypeScript 类型和 API 客户端代码
 */

import { execSync } from 'child_process'
import fs from 'fs'
import path from 'path'

const API_DOC_URL = process.env.API_DOC_URL || 'http://localhost:8080/v3/api-docs'
const OUTPUT_DIR = path.resolve(__dirname, '../src/api/generated')
const TEMP_SPEC_FILE = path.resolve(__dirname, 'temp-openapi-spec.json')

async function generateApiTypes() {
  console.log('🚀 开始生成 API 类型定义...')
  console.log(`📡 文档地址: ${API_DOC_URL}`)
  console.log(`📁 输出目录: ${OUTPUT_DIR}`)

  try {
    // 1. 下载 OpenAPI 规范
    console.log('\n📥 下载 OpenAPI 规范...')
    const specResponse = await fetch(API_DOC_URL)
    if (!specResponse.ok) {
      throw new Error(`下载失败: ${specResponse.status} ${specResponse.statusText}`)
    }
    const spec = await specResponse.json()
    fs.writeFileSync(TEMP_SPEC_FILE, JSON.stringify(spec, null, 2))
    console.log('✅ OpenAPI 规范下载完成')

    // 2. 创建输出目录
    if (!fs.existsSync(OUTPUT_DIR)) {
      fs.mkdirSync(OUTPUT_DIR, { recursive: true })
    }

    // 3. 运行 openapi-typescript-codegen
    console.log('\n⚙️ 生成 TypeScript 类型...')
    execSync(
      `npx openapi-typescript-codegen \
        -i ${TEMP_SPEC_FILE} \
        -o ${OUTPUT_DIR} \
        -c axios \
        --name kubo-api \
        --use-options \
        --axios-instance axios \
        --use-union-types \
        --enum-extension "= ${OUTPUT_DIR}/enums.ts" \
        --http-client axios \
        --export-core true \
        --export-services true \
        --export-schemas true \
        --export-hooks false \
        --generate-meta false`,
      { stdio: 'inherit', cwd: path.resolve(__dirname, '..') }
    )

    // 4. 清理临时文件
    fs.unlinkSync(TEMP_SPEC_FILE)

    // 5. 生成统一导出文件
    generateIndexFile()

    console.log('\n✅ API 类型生成完成!')
    console.log(`📁 输出目录: ${OUTPUT_DIR}`)
  } catch (error) {
    console.error('\n❌ 生成失败:', error)
    process.exit(1)
  }
}

function generateIndexFile() {
  const indexContent = `/**
 * 自动生成的 API 导出文件
 * 请勿手动修改，运行 \`npm run api:generate\` 重新生成
 */

// 核心类型导出
export * from './core'

// 模型导出
export * from './models'

// 服务导出
export * from './services'

// 默认导出
import { API } from './services'
import { axiosInstance } from './core'

export default {
  axiosInstance,
  ...API,
}
`

  fs.writeFileSync(path.join(OUTPUT_DIR, 'index.ts'), indexContent)
}

generateApiTypes()