# 前端国际化 (i18n) 方案设计

> **版本**：v1.0  
> **状态**：🟢 已定稿，强制执行  
> **适用范围**：Kubo Web 前端全平台

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| **多语言支持** | 中文(简体/繁体)、英文、可扩展其他语言 |
| **运行时切换** | 无需刷新页面，即时生效 |
| **持久化** | 用户语言偏好本地存储，下次访问自动恢复 |
| **按需加载** | 语言包按需懒加载，减少首屏体积 |
| **类型安全** | TypeScript 类型推导，键名提示、缺失检测 |
| **插值/复数/格式化** | 支持变量插值、复数形式、日期/数字/货币格式化 |

---

## 2. 技术选型

| 方案 | 评价 | 决策 |
|------|------|------|
| **vue-i18n v9+** | Vue 3 官方推荐、Composition API 原生支持、类型安全、生态成熟 | ✅ **选中** |
| vue-i18n v8 | Vue 2 遗留版本 | ❌ |
| i18next | 生态强、但需额外适配 Vue | ❌ |
| 自研方案 | 维护成本高 | ❌ |

### 核心依赖
```json
{
  "vue-i18n": "^9.13.0",
  "@intlify/unplugin-vue-i18n": "^6.0.0"  // Vite 插件：编译时优化、类型生成
}
```

---

## 3. 目录结构

```
src/locales/
├── index.ts              # i18n 实例创建、导出
├── config.ts             # 配置：支持语言、默认语言、回退规则
├── types.ts              # 类型定义：Schema、键名类型
├── utils.ts              # 工具函数：格式化、复数、日期
├── enums.ts              # 枚举翻译键
├── messages/             # 语言包目录 (按需加载)
│   ├── zh-CN/            # 中文简体
│   │   ├── index.ts      # 入口、合并所有模块
│   │   ├── common.ts     # 通用：按钮、提示、状态
│   │   ├── auth.ts       # 登录、注册、权限
│   │   ├── wms.ts        # 仓储模块
│   │   ├── mes.ts        # 制造模块
│   │   ├── finance.ts    # 财务模块
│   │   ├── system.ts     # 系统管理
│   │   ├── validation.ts # 校验提示
│   │   └── components.ts # 组件库通用
│   ├── en-US/            # 英文
│   │   ├── index.ts
│   │   ├── common.ts
│   │   └── ...
│   └── zh-TW/            # 中文繁体
│       ├── index.ts
│       └── ...
├── plugins/              # Vue 插件
│   └── i18n.ts          # app.use(i18n) 注册
└── composables/          # 组合式函数
    ├── useI18n.ts        # 增强版 useI18n
    ├── useLocale.ts      // 语言切换、持久化
    └── useFormat.ts      // 格式化工具
```

---

## 4. 核心实现

### 4.1 配置与类型定义

```typescript
// src/locales/config.ts
export const SUPPORTED_LOCALES = [
  { code: 'zh-CN', name: '中文 (简体)', flag: '🇨🇳', dir: 'ltr' },
  { code: 'en-US', name: 'English', flag: '🇺🇸', dir: 'ltr' },
  { code: 'zh-TW', name: '中文 (繁體)', flag: '🇹🇼', dir: 'ltr' },
] as const;

export type LocaleCode = typeof SUPPORTED_LOCALES[number]['code'];

export const DEFAULT_LOCALE: LocaleCode = 'zh-CN';

export const FALLBACK_LOCALE: LocaleCode = 'en-US';

export const LOCALE_STORAGE_KEY = 'kubo-locale';

// 语言包加载配置
export const LOCALE_LOAD_CONFIG = {
  // 预加载：默认语言 + 回退语言
  preload: [DEFAULT_LOCALE, FALLBACK_LOCALE],
  // 懒加载：其他语言
  lazy: true,
  // 缓存时间
  cacheTTL: 1000 * 60 * 60 * 24, // 24h
};
```

```typescript
// src/locales/types.ts
// 定义所有翻译键的 Schema，获得类型提示和完整性检查
export interface I18nSchema {
  common: {
    confirm: string;
    cancel: string;
    save: string;
    delete: string;
    edit: string;
    create: string;
    search: string;
    reset: string;
    submit: string;
    loading: string;
    success: string;
    failed: string;
    confirmDelete: string;
    yes: string;
    no: string;
    close: string;
    back: string;
    next: string;
    previous: string;
    submit: string;
    reset: string;
  };
  auth: {
    login: string;
    logout: string;
    username: string;
    password: string;
    rememberMe: string;
    forgotPassword: string;
    loginSuccess: string;
    loginFailed: string;
    invalidCredentials: string;
    accountLocked: string;
    sessionExpired: string;
  };
  validation: {
    required: string;
    minLength: string;
    maxLength: string;
    email: string;
    phone: string;
    number: string;
    positiveNumber: string;
    emailFormat: string;
    phoneFormat: string;
  };
  wms: {
    inventory: string;
    stock: string;
    inbound: string;
    outbound: string;
    transfer: string;
    check: string;
    warehouse: string;
    location: string;
    material: string;
    batch: string;
    quantity: string;
    availableQty: string;
    lockedQty: string;
    frozenQty: string;
  };
  // ... 其他模块
  components: {
    table: {
      noData: string;
      loading: string;
      total: string;
      pageSize: string;
      currentPage: string;
      totalItems: string;
    };
    form: {
      required: string;
      placeholder: string;
    };
    modal: {
      confirmTitle: string;
      confirmContent: string;
    };
    select: {
      placeholder: string;
      noMatch: string;
      loading: string;
    };
    pagination: {
      total: string;
      pageSize: string;
      goto: string;
      page: string;
    };
  };
  // 日期时间格式
  datetime: {
    formats: {
      short: string;      // 'yyyy/MM/dd'
      medium: string;     // 'yyyy/MM/dd HH:mm'
      long: string;       // 'yyyy年M月d日 HH:mm:ss'
      time: string;       // 'HH:mm:ss'
      date: string;       // 'yyyy-MM-dd'
    };
    relative: {
      justNow: string;
      minutesAgo: string;
      hoursAgo: string;
      daysAgo: string;
      weeksAgo: string;
      monthsAgo: string;
      yearsAgo: string;
    };
  };
  // 数字/货币格式
  number: {
    currency: string;     // '¥#,##0.00'
    percent: string;      // '#,##0%'
    decimal: string;      // '#,##0.00'
    integer: string;      // '#,##0'
  };
}

// 导出键名类型，获得完整提示
export type I18nKey = keyof I18nSchema;
export type NestedKeyOf<T> = T extends object
  ? { [K in keyof T]: K extends string ? (T[K] extends object ? `${K}.${NestedKeyOf<T[K]>}` : K) : never }[keyof T]
  : never;

export type I18nKeyPath = NestedKeyOf<I18nSchema>;
```

### 4.2 i18n 实例创建

```typescript
// src/locales/index.ts
import { createI18n } from 'vue-i18n';
import type { I18nSchema, LocaleCode } from './types';
import { SUPPORTED_LOCALES, DEFAULT_LOCALE, FALLBACK_LOCALE, LOCALE_LOAD_CONFIG } from './config';

// 导入预加载语言包
import zhCN from './messages/zh-CN/index';
import enUS from './messages/en-US/index';

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
} as const;

export const i18n = createI18n<I18nSchema, LocaleCode>({
  legacy: false,                    // 使用 Composition API 模式
  globalInjection: true,            // 全局注入 $t、$tc、$te 等
  locale: DEFAULT_LOCALE,
  fallbackLocale: FALLBACK_LOCALE,
  availableLocales: SUPPORTED_LOCALES.map(l => l.code),
  messages,
  missingWarn: import.meta.env.DEV,
  fallbackWarn: import.meta.env.DEV,
  missingHandler: (locale, key) => {
    console.warn(`[i18n] Missing translation: ${key} for locale ${locale}`);
    return key;
  },
  fallbackRoot: true,               // 允许回退到根键
  silentTranslationWarn: !import.meta.env.DEV,
  silentFallbackWarn: !import.meta.env.DEV,
  // 日期时间格式化器
  datetimeFormats: {
    'zh-CN': {
      short: { year: 'numeric', month: '2-digit', day: '2-digit' },
      medium: { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' },
      long: { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' },
      time: { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false },
      date: { year: 'numeric', month: '2-digit', day: '2-digit' },
    },
    'en-US': {
      short: { year: 'numeric', month: '2-digit', day: '2-digit' },
      medium: { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' },
      long: { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' },
      time: { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true },
      date: { year: 'numeric', month: '2-digit', day: '2-digit' },
    },
  },
  // 数字格式
  numberFormats: {
    'zh-CN': {
      currency: { style: 'currency', currency: 'CNY', minimumFractionDigits: 2 },
      percent: { style: 'percent', minimumFractionDigits: 0 },
      decimal: { style: 'decimal', minimumFractionDigits: 2 },
      integer: { style: 'decimal', minimumFractionDigits: 0 },
    },
    'en-US': {
      currency: { style: 'currency', currency: 'USD', minimumFractionDigits: 2 },
      percent: { style: 'percent', minimumFractionDigits: 0 },
      decimal: { style: 'decimal', minimumFractionDigits: 2 },
      integer: { style: 'decimal', minimumFractionDigits: 0 },
    },
  },
});

// 类型声明增强
declare module 'vue-i18n' {
  export interface DefineLocaleMessage extends I18nSchema {}
  export interface DefineDateTimeFormat {
    'zh-CN': typeof datetimeFormats['zh-CN'];
    'en-US': typeof datetimeFormats['en-US'];
  }
  export interface DefineNumberFormat {
    'zh-CN': typeof numberFormats['zh-CN'];
    'en-US': typeof numberFormats['en-US'];
  }
}

export default i18n;
```

### 4.3 语言包懒加载与预加载

```typescript
// src/locales/utils.ts
import type { LocaleCode } from './config';
import { SUPPORTED_LOCALES, LOCALE_LOAD_CONFIG } from './config';

// 语言包加载缓存
const loadedLocales = new Set<LocaleCode>();
const loadingPromises = new Map<LocaleCode, Promise<void>>();

/**
 * 懒加载语言包
 */
export async function loadLocale(locale: LocaleCode): Promise<void> {
  if (loadedLocales.has(locale)) return;
  
  if (loadingPromises.has(locale)) {
    return loadingPromises.get(locale)!;
  }

  const promise = (async () => {
    try {
      // 动态导入语言包入口
      const module = await import(`./messages/${locale}/index`);
      const messages = module.default || module;
      
      // 注册到 i18n
      i18n.global.setLocaleMessage(locale, messages);
      loadedLocales.add(locale);
    } catch (error) {
      console.error(`[i18n] Failed to load locale: ${locale}`, error);
      throw error;
    }
  })();

  loadingPromises.set(locale, promise);
  return promise;
}

/**
 * 预加载默认语言和回退语言
 */
export async function preloadLocales(): Promise<void> {
  await Promise.all(
    LOCALE_LOAD_CONFIG.preload.map(loadLocale)
  );
}

/**
 * 切换语言
 */
export async function switchLocale(locale: LocaleCode): Promise<void> {
  if (!SUPPORTED_LOCALES.some(l => l.code === locale)) {
    throw new Error(`Unsupported locale: ${locale}`);
  }

  await loadLocale(locale);
  i18n.global.locale.value = locale;
  
  // 持久化
  localStorage.setItem('kubo-locale', locale);
  
  // 触发自定义事件，通知其他组件
  window.dispatchEvent(new CustomEvent('locale-changed', { detail: locale }));
  
  // 更新 HTML lang 属性
  document.documentElement.lang = locale;
}

/**
 * 获取当前语言
 */
export function getCurrentLocale(): string {
  return i18n.global.locale.value;
}

/**
 * 获取浏览器首选语言
 */
export function getBrowserLocale(): string {
  const navLang = navigator.language || (navigator as any).userLanguage;
  const matched = SUPPORTED_LOCALES.find(l => l.code.startsWith(navLang.split('-')[0]));
  return matched?.code || 'zh-CN';
}

/**
 * 初始化语言 (应用启动时调用)
 */
export async function initI18n(): Promise<void> {
  // 1. 从本地存储获取
  const saved = localStorage.getItem('kubo-locale') as any;
  const browserLang = getBrowserLocale();
  const initialLocale = SUPPORTED_LOCALES.some(l => l.code === saved) ? saved : browserLang;
  
  // 2. 预加载默认语言
  await preloadLocales();
  
  // 3. 加载并设置初始语言
  await loadLocale(initialLocale);
  i18n.global.locale.value = initialLocale;
  
  // 4. 监听存储变化 (多标签页同步)
  window.addEventListener('storage', (e) => {
    if (e.key === 'kubo-locale' && e.newValue) {
      const newLocale = e.newValue as any;
      if (SUPPORTED_LOCALES.some(l => l.code === newLocale)) {
        switchLocale(newLocale);
      }
    }
  });
}
```

### 4.4 增强版 Composable

```typescript
// src/locales/composables/useI18n.ts
import { useI18n as useVueI18n } from 'vue-i18n';
import type { I18nKeyPath } from '../types';
import { i18n, type I18nSchema } from '../index';

/**
 * 增强版 useI18n，提供类型安全的翻译函数
 */
export function useI18n() {
  const { t, tc, te, d, n, locale, availableLocales, setLocaleMessage } = useVueI18n<I18nSchema>();

  // 类型安全的翻译函数
  const $t = <K extends keyof I18nSchema>(key: K, ...args: any[]) => {
    return t(key as any, ...args);
  };

  // 类型安全的复数翻译
  const $tc = <K extends keyof I18nSchema>(key: K, count: number, ...args: any[]) => {
    return tc(key as any, count, ...args);
  };

  // 检查键是否存在
  const $te = (key: string) => te(key);

  // 日期格式化
  const $d = (value: Date | number | string, format?: keyof typeof datetimeFormats['zh-CN']) => {
    return d(value, format);
  };

  // 数字格式化
  const $n = (value: number, format?: keyof typeof numberFormats['zh-CN']) => {
    return n(value, format);
  };

  return {
    t: $t,
    tc: $tc,
    te: $te,
    d: $d,
    n: $n,
    locale,
    availableLocales,
    setLocaleMessage,
  };
}

/**
 * 语言切换 Composable
 */
export function useLocale() {
  const { locale, availableLocales } = useVueI18n();
  const { switchLocale, loadLocale, getCurrentLocale } = await import('../utils');

  const currentLocale = computed(() => locale.value);
  const supportedLocales = computed(() => SUPPORTED_LOCALES);

  const changeLocale = async (locale: string) => {
    await switchLocale(locale as any);
  };

  return {
    locale: currentLocale,
    availableLocales: supportedLocales,
    changeLocale,
    loadLocale,
  };
}

/**
 * 格式化工具 Composable
 */
export function useFormat() {
  const { d, n } = useI18n();

  // 相对时间格式化
  const relativeTime = (date: Date | number | string): string => {
    const now = Date.now();
    const then = typeof date === 'string' || typeof date === 'number' ? new Date(date).getTime() : date.getTime();
    const diff = now - then;

    const minute = 60 * 1000;
    const hour = minute * 60;
    const day = hour * 24;
    const week = day * 7;
    const month = day * 30;
    const year = day * 365;

    if (diff < minute) return '刚刚';
    if (diff < hour) return `${Math.floor(diff / minute)}分钟前`;
    if (diff < day) return `${Math.floor(diff / hour)}小时前`;
    if (diff < week) return `${Math.floor(diff / day)}天前`;
    if (diff < month) return `${Math.floor(diff / week)}周前`;
    if (diff < year) return `${Math.floor(diff / month)}个月前`;
    return `${Math.floor(diff / year)}年前`;
  };

  // 数字格式化：千分位
  const formatNumber = (value: number, decimals = 0): string => {
    return new Intl.NumberFormat('zh-CN', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(value);
  };

  // 货币格式化
  const formatCurrency = (value: number, currency = 'CNY', decimals = 2): string => {
    return new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency,
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(value);
  };

  // 百分比格式化
  const formatPercent = (value: number, decimals = 0): string => {
    return new Intl.NumberFormat('zh-CN', {
      style: 'percent',
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(value);
  };

  // 文件大小格式化
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // 日期格式化
  const formatDate = (date: Date | string | number, format: 'short' | 'medium' | 'long' | 'time' = 'medium'): string => {
    const d = new Date(date);
    const options: Record<string, Intl.DateTimeFormatOptions> = {
      short: { year: 'numeric', month: '2-digit', day: '2-digit' },
      medium: { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' },
      long: { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' },
      time: { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false },
    };
    return new Intl.DateTimeFormat('zh-CN', options[format]).format(d);
  };

  // 相对时间
  const relative = (date: Date | string | number): string => {
    return relativeTime(date);
  };

  return {
    relativeTime,
    formatNumber,
    formatCurrency,
    formatPercent,
    formatFileSize,
    formatDate,
    relative,
  };
}
```

---

## 5. 语言包示例

```typescript
// src/locales/messages/zh-CN/common.ts
export default {
  confirm: '确认',
  cancel: '取消',
  save: '保存',
  delete: '删除',
  edit: '编辑',
  create: '新建',
  search: '搜索',
  reset: '重置',
  submit: '提交',
  loading: '加载中...',
  success: '成功',
  failed: '失败',
  confirmDelete: '确定要删除吗？此操作不可撤销。',
  yes: '是',
  no: '否',
  close: '关闭',
  back: '返回',
  next: '下一步',
  previous: '上一步',
  submit: '提交',
  reset: '重置',
  expand: '展开',
  collapse: '折叠',
  refresh: '刷新',
  export: '导出',
  import: '导入',
  print: '打印',
  copy: '复制',
  copied: '已复制',
  copyFailed: '复制失败',
  noData: '暂无数据',
  noResult: '无匹配结果',
  pleaseSelect: '请选择',
  pleaseInput: '请输入',
  required: '必填项',
  optional: '选填',
  all: '全部',
  none: '无',
  enable: '启用',
  disable: '禁用',
  enabled: '已启用',
  disabled: '已禁用',
  online: '在线',
  offline: '离线',
  running: '运行中',
  stopped: '已停止',
  pending: '待处理',
  processing: '处理中',
  completed: '已完成',
  failed: '失败',
  cancelled: '已取消',
  draft: '草稿',
  published: '已发布',
  archived: '已归档',
  today: '今天',
  yesterday: '昨天',
  tomorrow: '明天',
  thisWeek: '本周',
  thisMonth: '本月',
  thisYear: '今年',
  custom: '自定义',
  actions: '操作',
  details: '详情',
  preview: '预览',
  download: '下载',
  upload: '上传',
  dragDrop: '拖拽或点击上传',
  dragDropHint: '支持拖拽文件到此处，或点击选择文件',
  fileTooLarge: '文件大小超过限制',
  fileTypeNotAllowed: '文件类型不支持',
  uploadSuccess: '上传成功',
  uploadFailed: '上传失败',
  downloadSuccess: '下载成功',
  downloadFailed: '下载失败',
  networkError: '网络错误，请检查连接',
  serverError: '服务器错误，请稍后重试',
  unauthorized: '未授权，请重新登录',
  forbidden: '权限不足',
  notFound: '资源不存在',
  timeout: '请求超时',
  unknownError: '未知错误',
  confirm: '确定',
  cancel: '取消',
  ok: '确定',
  back: '返回',
  next: '下一步',
  prev: '上一步',
  finish: '完成',
  skip: '跳过',
  retry: '重试',
  ignore: '忽略',
  view: '查看',
  detail: '详情',
  more: '更多',
  less: '收起',
  expandAll: '全部展开',
  collapseAll: '全部折叠',
  selectAll: '全选',
  deselectAll: '取消全选',
  invertSelection: '反选',
  clearSelection: '清空选择',
  selectedCount: '已选择 {count} 项',
  searchPlaceholder: '搜索...',
  filter: '筛选',
  sort: '排序',
  ascending: '升序',
  descending: '降序',
  columnSettings: '列设置',
  density: '密度',
  compact: '紧凑',
  comfortable: '舒适',
  normal: '标准',
  theme: '主题',
  light: '浅色',
  dark: '深色',
  system: '跟随系统',
  language: '语言',
  profile: '个人中心',
  settings: '设置',
  logout: '退出登录',
  login: '登录',
  register: '注册',
  username: '用户名',
  password: '密码',
  confirmPassword: '确认密码',
  email: '邮箱',
  phone: '手机号',
  verificationCode: '验证码',
  getCode: '获取验证码',
  resendCode: '重新发送 ({seconds}s)',
  forgotPassword: '忘记密码',
  resetPassword: '重置密码',
  changePassword: '修改密码',
  oldPassword: '原密码',
  newPassword: '新密码',
  confirmNewPassword: '确认新密码',
  passwordMismatch: '两次输入的密码不一致',
  passwordTooWeak: '密码强度不足',
  passwordRequirements: '至少 8 位，包含大小写字母、数字、特殊字符',
  rememberMe: '记住我',
  login: '登录',
  logout: '退出登录',
  loggingIn: '登录中...',
  loginSuccess: '登录成功',
  loginFailed: '登录失败',
  welcome: '欢迎使用',
  welcomeBack: '欢迎回来',
};
```

---

## 6. 使用示例

```vue
<!-- 组件中使用 -->
<script setup lang="ts">
import { useI18n, useFormat, useLocale } from '@/locales';

const { t, tc, locale, changeLocale } = useI18n();
const { formatCurrency, formatDate, relativeTime } = useFormat();
const { locale: currentLocale, changeLocale: switchLang } = useLocale();

// 插值
const userName = '张三';
const welcomeMsg = t('auth.welcomeBack', { name: userName }); // "欢迎回来，张三"

// 复数
const itemCount = 5;
const itemMsg = tc('common.itemCount', itemCount); // "5 项"

// 日期格式化
const now = new Date();
const formatted = formatDate(now, 'medium'); // "2026/08/01 14:30"

// 相对时间
const relative = relativeTime(Date.now() - 3600000); // "1小时前"

// 货币格式化
const price = formatCurrency(1234.56); // "¥1,234.56"

// 切换语言
const handleLangChange = async (lang: string) => {
  await switchLang(lang);
};
</script>

<template>
  <div>
    <el-select v-model="locale" @change="handleLangChange" placeholder="选择语言">
      <el-option v-for="lang in supportedLocales" :key="lang.code" :label="lang.name" :value="lang.code" />
    </el-select>
    
    <p>{{ t('common.welcome') }}</p>
    <p>{{ t('auth.welcomeBack', { name: userName }) }}</p>
    <p>{{ tc('common.itemCount', itemCount) }}</p>
    <p>{{ formatCurrency(1234.56) }}</p>
    <p>{{ formatDate(new Date(), 'medium') }}</p>
    <p>{{ relativeTime(lastLoginTime) }}</p>
  </div>
</template>
```

---

## 7. Vite 插件配置 (编译时优化)

```typescript
// vite.config.ts
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite';

export default defineConfig({
  plugins: [
    VueI18nPlugin({
      include: resolve(__dirname, 'src/locales/**'),
      compositionOnly: true,
      fullInstall: true,
      runtimeOnly: false,
      // 编译时类型生成
      include: [resolve(__dirname, 'src/locales/**')],
      // 启用严格模式
      strictMessage: true,
      // 编译时错误检查
      forceStringify: true,
    }),
  ],
});
```

---

## 7. 测试与构建

```bash
# 安装依赖
pnpm add vue-i18n@^9.13.0 @intlify/unplugin-vue-i18n@^6.0.0

# 类型检查
pnpm run typecheck

# 构建检查
pnpm run build

# 运行测试
pnpm run test:unit
```

---

*文档版本：v1.0*  
*最后更新：2026-08-01*  
*状态：🟢 已定稿，强制执行*