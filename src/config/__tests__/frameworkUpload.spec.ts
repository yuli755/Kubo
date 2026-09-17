// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { api } from '@/api/axios'
import KUpload from '@/component/framework/KUpload.vue'
import { DialogUtil, UploadUtil } from '@/framework'

/**
 * # 上传组件（批次 0B 的前置件）
 *
 * 这一批只做"上传"这一件事，但它一次性解锁三处：
 * 批 0B 的单据表单件与 `UserCard`/`component/user/*`、批 2B 的合同编辑器/参与方编辑器/选择器，
 * 以及 wms、channel 两域的全部编辑器 —— 所以行为要钉死，不能只"看着能传"。
 *
 * 契约三件（都来自真实的后端与调用点）：
 * 1. **URL 不能拼歪**：`WebConfig.uploadUrl = '/api/file/upload'` 是给 airpower 的，
 *    本仓 axios 实例 `baseURL = '/api'` —— 原样传会变成 `/api/api/file/upload`（这个错只在真机上报）。
 * 2. **要带 `category`**：后端 `FileController.upload` 按类别落库，缺了会进"普通文件"。
 * 3. **响应要补 `size`/`name`**：后端可能给 0/空，列表会显示成空白一行。
 */

const imageFile = () => new File(['x'], 'logo.png', { type: 'image/png' })
const pdfFile = () => new File(['hello'], '合同附件.pdf', { type: 'application/pdf' })

/** jsdom 的 `input.files` 是只读的，只能 redefine 后再派发 change */
async function pickFile(input: HTMLInputElement, file: File): Promise<void> {
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  input.dispatchEvent(new Event('change', { bubbles: true }))
  await flushPromises()
}

function findButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll('button'))
    .find(button => button.textContent?.trim() === text) as HTMLButtonElement | undefined
}

describe('framework · UploadUtil（校验与请求契约）', () => {
  it('按 accept 与大小上限校验，失败时给中文原因', () => {
    expect(UploadUtil.validate(pdfFile(), { accept: '.pdf' })).toBeNull()
    expect(UploadUtil.validate(imageFile(), { accept: '.pdf' })).toContain('只允许上传')
    expect(UploadUtil.validate(imageFile(), { accept: 'image/*' })).toBeNull()
    expect(UploadUtil.validate(pdfFile(), { accept: 'image/*' })).toContain('只允许上传')
    // 大小：默认 10MB
    const big = new File(['x'], 'big.zip', { type: 'application/zip' })
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 })
    expect(UploadUtil.validate(big, {})).toContain('不能超过 10 MB')
    expect(UploadUtil.validate(big, { maxSizeMB: 20 })).toBeNull()
  })

  it('请求地址剥掉了统一实例已经带的 /api 前缀（否则会变成 /api/api/...）', () => {
    expect(UploadUtil.getRequestUrl()).toBe('file/upload')
  })

  it('上传时带上 category，并把响应里的 name/size/extension 补齐', async () => {
    const spy = vi.spyOn(api, 'upload').mockResolvedValue({ id: 7, url: 'upload/2026/a.png', category: 1001 } as never)

    const record = await UploadUtil.upload(imageFile(), { category: 1001 })

    expect(spy).toHaveBeenCalledTimes(1)
    const [url, file, option] = spy.mock.calls[0] as unknown as [string, File, { fields?: Record<string, unknown> }]
    expect(url).toBe('file/upload')
    expect(file.name).toBe('logo.png')
    expect(option.fields).toEqual({ category: 1001 })

    expect(record.id).toBe(7)
    expect(record.url).toBe('upload/2026/a.png')
    // 后端没给 name/size → 用本地 File 兜底
    expect(record.name).toBe('logo.png')
    expect(record.size).toBe(1)
    expect(record.extension).toBe('png')
    spy.mockRestore()
  })

  it('类别缺省时不硬塞 category 字段（交给后端默认值）', async () => {
    const spy = vi.spyOn(api, 'upload').mockResolvedValue({ url: 'a.pdf' } as never)
    await UploadUtil.upload(pdfFile())
    const [, , option] = spy.mock.calls[0] as unknown as [string, File, { fields?: Record<string, unknown> }]
    expect(option.fields).toEqual({})
    spy.mockRestore()
  })

  it('校验不通过时直接抛错、不发请求', async () => {
    const spy = vi.spyOn(api, 'upload')
    await expect(UploadUtil.upload(pdfFile(), { accept: 'image/*' })).rejects.toThrow('只允许上传')
    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })

  it('展示辅助：大小、图片判定、静态地址前缀', () => {
    expect(UploadUtil.sizeText(512)).toBe('512 B')
    expect(UploadUtil.sizeText(2048)).toBe('2.0 KB')
    expect(UploadUtil.sizeText(5 * 1024 * 1024)).toBe('5.00 MB')
    expect(UploadUtil.sizeText(0)).toBe('-')
    expect(UploadUtil.isImage({ name: 'a.PNG' })).toBe(true)
    expect(UploadUtil.isImage({ url: 'upload/a/b.pdf' })).toBe(false)
    expect(UploadUtil.resolveUrl('upload/a.png')).toBe('/static/upload/a.png')
    // 已经是绝对地址就不动它
    expect(UploadUtil.resolveUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png')
  })
})

describe('framework · KUpload（组件行为）', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('选中文件后上传，并把记录并进 v-model；列表里显示文件名与大小', async () => {
    vi.spyOn(api, 'upload').mockResolvedValue({ id: 1, url: 'upload/logo.png', size: 1, extension: 'png' } as never)
    const wrapper = mount(KUpload as never, { props: { modelValue: [], category: 1001 } } as never)

    const input = wrapper.find('input[type="file"]').element as HTMLInputElement
    await pickFile(input, imageFile())
    await nextTick()

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted, '没有把上传结果写回 v-model').toBeTruthy()
    const files = emitted![0]![0] as { name: string, size: number }[]
    expect(files.map(f => f.name)).toEqual(['logo.png'])

    await wrapper.setProps({ modelValue: files } as never)
    expect(wrapper.text()).toContain('logo.png')
    expect(wrapper.text()).toContain('1 B')
  })

  it('类型不符合 accept 时给提示且不发请求', async () => {
    const spy = vi.spyOn(api, 'upload')
    const wrapper = mount(KUpload as never, { props: { modelValue: [], accept: 'image/*' } } as never)

    await pickFile(wrapper.find('input[type="file"]').element as HTMLInputElement, pdfFile())

    expect(spy).not.toHaveBeenCalled()
    expect(wrapper.emitted('failed')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })

  it('单选时新文件替换旧文件；删除按钮把列表清空', async () => {
    vi.spyOn(api, 'upload').mockResolvedValue({ url: 'upload/a.png', size: 1 } as never)
    const first = { name: 'old.png', url: 'upload/old.png', size: 1 }
    const wrapper = mount(KUpload as never, { props: { modelValue: [first] } } as never)

    // 单选且已有文件时不再显示选择区（避免"到底是替换还是追加"的歧义）
    expect(wrapper.find('.k-upload__picker').exists()).toBe(false)

    await wrapper.findAll('.k-upload__remove')[0]!.trigger('click')
    expect(wrapper.emitted('update:modelValue')![0]![0]).toEqual([])
  })

  it('只读模式不显示选择与删除（详情页展示）', () => {
    const wrapper = mount(KUpload as never, {
      props: { modelValue: [{ name: 'a.pdf', url: 'upload/a.pdf', size: 10 }], readonly: true },
    } as never)
    expect(wrapper.find('.k-upload__picker').exists()).toBe(false)
    expect(wrapper.find('.k-upload__remove').exists()).toBe(false)
    expect(wrapper.text()).toContain('a.pdf')
  })
})

describe('framework · DialogUtil.showUpload（命令式上传弹窗）', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('选文件 → 确定：resolve 上传记录，并把 category 带给后端', async () => {
    const spy = vi.spyOn(api, 'upload').mockResolvedValue({ id: 9, url: 'upload/ct.pdf', size: 5, extension: 'pdf' } as never)

    const promise = DialogUtil.showUpload<{ name: string, url: string }>({ category: 2001 })
    await vi.waitFor(() => {
      expect(document.querySelector('.k-upload__picker'), '上传弹窗没打开').toBeTruthy()
    }, { timeout: 5000 })

    // 未选文件时「确定」不可用
    expect(findButton('确定')?.disabled).toBe(true)

    const input = document.querySelector('.k-upload__input') as HTMLInputElement
    await pickFile(input, pdfFile())
    await vi.waitFor(() => {
      expect(document.querySelector('.k-upload__name')?.textContent).toBe('合同附件.pdf')
    }, { timeout: 5000 })

    const confirm = findButton('确定')!
    expect(confirm.disabled).toBe(false)
    confirm.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    const record = await promise
    expect(record?.name).toBe('合同附件.pdf')
    expect(record?.url).toBe('upload/ct.pdf')
    const [, , option] = spy.mock.calls[0] as unknown as [string, File, { fields?: Record<string, unknown> }]
    expect(option.fields).toEqual({ category: 2001 })
  })

  it('兼容 airpower 旧契约 { data: { category } }', async () => {
    const spy = vi.spyOn(api, 'upload').mockResolvedValue({ url: 'upload/a.png', size: 1 } as never)

    const promise = DialogUtil.showUpload({ data: { category: 1001 } })
    await vi.waitFor(() => {
      expect(document.querySelector('.k-upload__input')).toBeTruthy()
    }, { timeout: 5000 })

    await pickFile(document.querySelector('.k-upload__input') as HTMLInputElement, imageFile())
    await vi.waitFor(() => {
      expect(document.querySelector('.k-upload__name')).toBeTruthy()
    }, { timeout: 5000 })

    findButton('确定')!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await promise

    const [, , option] = spy.mock.calls[0] as unknown as [string, File, { fields?: Record<string, unknown> }]
    expect(option.fields).toEqual({ category: 1001 })
  })

  it('取消时 resolve undefined（调用方必须判空）', async () => {
    const promise = DialogUtil.showUpload({ category: 1 })
    await vi.waitFor(() => {
      expect(findButton('取消')).toBeTruthy()
    }, { timeout: 5000 })

    findButton('取消')!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await expect(promise).resolves.toBeUndefined()
  })
})
