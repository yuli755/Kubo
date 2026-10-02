import type { LegalKind } from '@/config/legal'
import { describe, expect, it } from 'vitest'
import { LEGAL_DOCUMENTS, legalDocument } from '@/config/legal'

/**
 * 法律文本文档契约测试。
 *
 * 锁的不是文案措辞（法务会改），而是**结构完整性与诚实性**：
 * - 两篇都存在、有标题/日期/前言/若干章节/脚注 —— 防止有人把数据掏空后页面静默空白；
 * - 隐私政策必须如实披露「错误上报」：R40 起错误上报携带身份头（Authorization/租户），
 *   文档若删掉这条就是**已知行为与披露脱节**，这是合规红线，不是文风问题。
 */

describe('config/legal · 内置法律文本契约', () => {
  const kinds: LegalKind[] = ['privacy', 'terms']

  it.each(kinds)('《%s》结构完整：标题/日期/前言/章节/脚注齐备', (kind) => {
    const doc = LEGAL_DOCUMENTS[kind]
    expect(doc.title.length).toBeGreaterThan(1)
    expect(doc.updated).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(doc.preamble.length).toBeGreaterThan(20)
    expect(doc.sections.length).toBeGreaterThanOrEqual(3)
    for (const section of doc.sections) {
      expect(section.heading.length).toBeGreaterThan(1)
      expect(section.paragraphs.length).toBeGreaterThan(0)
      for (const paragraph of section.paragraphs) {
        expect(paragraph.trim().length).toBeGreaterThan(5)
      }
    }
    expect(doc.footnote).toContain('正式部署')
  })

  it('两篇标题各自正确（路由页的标题不会被写反）', () => {
    expect(LEGAL_DOCUMENTS.privacy.title).toBe('隐私政策')
    expect(LEGAL_DOCUMENTS.terms.title).toBe('服务条款')
  })

  it('隐私政策如实披露错误上报（R40 起上报携带身份 —— 披露必须与行为一致）', () => {
    const privacy = JSON.stringify(LEGAL_DOCUMENTS.privacy)
    expect(privacy).toContain('错误上报')
    expect(privacy).toContain('令牌')
  })

  it('服务条款覆盖账号安全与数据真实性（内部系统的两条生命线）', () => {
    const terms = JSON.stringify(LEGAL_DOCUMENTS.terms)
    expect(terms).toContain('账号')
    expect(terms).toContain('如实')
  })

  it('未知 kind 返回 null，由视图兜底（而不是渲染 undefined）', () => {
    expect(legalDocument('nope' as LegalKind)).toBeNull()
    expect(legalDocument('privacy')).not.toBeNull()
  })
})
