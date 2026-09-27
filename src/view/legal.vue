<script lang="ts" setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * # 法律/帮助静态页（访客可访问）
 *
 * 登录页的《隐私政策》《服务条款》「忘记密码？」三个入口过去指向不存在的路径
 * （点击后落到兜底错误页），本组件用「一条路由一份内容」收口：
 * `/privacy`、`/terms`、`/forgot-password` 共用同一个访客布局。
 *
 * 条款正文为平台通用模板文案，正式上线前应替换为企业法务审定的版本；
 * 「忘记密码」不提供自助重置（平台未开通邮箱找回通道），给出管理员重置指引。
 */

const route = useRoute()

interface Section {
  title: string
  paragraphs: string[]
}

interface PageDoc {
  title: string
  updated: string
  notice?: string
  sections: Section[]
}

const DOCS: Record<string, PageDoc> = {
  '/privacy': {
    title: '隐私政策',
    updated: '2026-09-14',
    notice: '本文为平台通用模板文本，正式版本以企业法务审定为准。',
    sections: [
      {
        title: '一、我们收集的信息',
        paragraphs: [
          '账号信息：注册/登录时提供的用户名、邮箱、手机号与密码（密码以加密形式存储，平台不保存明文）。',
          '业务数据：您在使用仓储、生产、销售等模块过程中录入的物料、单据与设备数据。',
          '设备与日志信息：为保障服务安全与稳定，我们记录访问日志（登录 IP、时间、操作结果）与设备采集上报的运行参数。',
        ],
      },
      {
        title: '二、信息的使用目的',
        paragraphs: [
          '提供账号认证、权限控制与业务单据流转等平台核心功能。',
          '进行安全审计与风险防控（如登录异常检测、接口限流）。',
          '在法律法规要求的范围内配合监管与审计。',
        ],
      },
      {
        title: '三、信息的存储与保护',
        paragraphs: [
          '数据存储于企业自有或受控的服务器，敏感字段（手机号、邮箱等）在列表展示时按列级脱敏策略处理。',
          '平台通过角色权限、行级数据权限与操作日志对数据访问实施最小授权与全程留痕。',
        ],
      },
      {
        title: '四、您的权利',
        paragraphs: [
          '您可以随时登录账号查阅、更正您的个人资料；如需注销账号或导出个人数据，请联系系统管理员。',
          '对平台数据处理规则有异议的，可通过企业内部分管渠道反馈。',
        ],
      },
    ],
  },
  '/terms': {
    title: '服务条款',
    updated: '2026-09-14',
    notice: '本文为平台通用模板文本，正式版本以企业法务审定为准。',
    sections: [
      {
        title: '一、服务范围',
        paragraphs: [
          '本平台为企业提供资产管理、渠道购销、仓储（WMS）、生产（MES）、设备物联（IoT）等一体化管理服务，具体开通范围以管理员配置为准。',
        ],
      },
      {
        title: '二、账号与安全',
        paragraphs: [
          '账号实行实名制分配，请勿转借、共享账号；因保管不善导致的损失由账号持有人承担。',
          '连续多次登录失败将触发临时锁定，属平台安全保护机制。',
        ],
      },
      {
        title: '三、用户行为规范',
        paragraphs: [
          '不得利用平台从事任何违法违规活动，不得擅自扫描、攻击或干扰平台服务。',
          '录入的业务数据应真实、准确；因虚假数据导致的决策后果由数据录入方承担。',
        ],
      },
      {
        title: '四、免责声明',
        paragraphs: [
          '因不可抗力、网络故障或第三方服务中断导致的服务暂停，平台将尽力恢复但不承担由此产生的间接损失。',
          '平台保留根据业务发展调整、更新本条款的权利，调整后将在本页面公示。',
        ],
      },
    ],
  },
  '/forgot-password': {
    title: '忘记密码',
    updated: '2026-09-14',
    sections: [
      {
        title: '如何重置密码',
        paragraphs: [
          '本平台暂不支持邮箱/短信自助重置，请按以下流程处理：',
          '1. 联系您所在组织的系统管理员，说明账号与重置原因；',
          '2. 管理员在「系统管理 → 员工管理」中为您重置初始密码；',
          '3. 使用初始密码登录后，请立即在右上角「个人设置」中修改为自己的密码。',
        ],
      },
      {
        title: '账号被锁定怎么办',
        paragraphs: [
          '连续 5 次密码错误会触发 15 分钟安全锁定，锁定期间提示「登录失败次数过多」。',
          '等待锁定自动解除，或联系管理员核实身份后处理。',
        ],
      },
    ],
  },
}

const doc = computed<PageDoc | undefined>(() => DOCS[route.path])
</script>

<template>
  <div class="legal-page">
    <div class="legal-card">
      <div class="legal-header">
        <h1>{{ doc?.title || '页面不存在' }}</h1>
        <p
          v-if="doc"
          class="updated"
        >
          最近更新：{{ doc.updated }}
        </p>
        <p
          v-if="doc?.notice"
          class="notice"
        >
          {{ doc.notice }}
        </p>
      </div>

      <div
        v-if="doc"
        class="legal-body"
      >
        <section
          v-for="section in doc.sections"
          :key="section.title"
        >
          <h2>{{ section.title }}</h2>
          <p
            v-for="(para, idx) in section.paragraphs"
            :key="idx"
          >
            {{ para }}
          </p>
        </section>
      </div>
      <div
        v-else
        class="legal-body"
      >
        <p>您访问的页面不存在，请从登录页重新进入。</p>
      </div>

      <div class="legal-footer">
        <RouterLink to="/login">
          返回登录
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.legal-page {
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 16px;
  background: linear-gradient(160deg, #eef2ff 0%, #f8fafc 45%, #f1f5f9 100%);

  @media (prefers-color-scheme: dark) {
    background: linear-gradient(160deg, #0f172a 0%, #111827 50%, #0f172a 100%);
  }
}

.legal-card {
  width: 100%;
  max-width: 760px;
  background: var(--el-bg-color, #fff);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgb(15 23 42 / 8%);
  padding: 40px 48px 32px;
}

.legal-header {
  text-align: center;
  margin-bottom: 24px;

  h1 {
    margin: 16px 0 4px;
    font-size: 24px;
    color: var(--el-text-color-primary, #1e293b);
  }

  .updated {
    margin: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary, #94a3b8);
  }

  .notice {
    margin: 12px 0 0;
    font-size: 12px;
    color: #b45309;
  }
}

.legal-body {
  section + section {
    margin-top: 24px;
  }

  h2 {
    font-size: 16px;
    margin: 0 0 8px;
    color: var(--el-text-color-primary, #334155);
  }

  p {
    margin: 6px 0;
    font-size: 14px;
    line-height: 1.8;
    color: var(--el-text-color-regular, #475569);
  }
}

.legal-footer {
  margin-top: 32px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter, #e2e8f0);
  text-align: center;

  a {
    font-size: 14px;
    color: var(--el-color-primary, #4f46e5);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
