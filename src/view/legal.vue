<script lang="ts" setup>
/**
 * 法律文本页（隐私政策 / 服务条款）—— R41 ①a
 *
 * ## 为什么存在
 * 登录页协议勾选旁的《隐私政策》《服务条款》此前是死链（点了被守卫弹回登录页，
 * §16.6 实测）。协议同意是登录的合规前置，「同意一个读不到的文本」比不同意更糟。
 *
 * ## 设计
 * 一页两路由（`/privacy`、`/terms`），kind 由路径推导；文案是数据，收在
 * `@/config/legal` 单一数据源里（与 palette 同手法），视图零文案、可被路由契约测试锁住。
 * `meta.guest: true`：登录前必须可读（这正是本页存在的理由），登录后也可读。
 */
import { ElButton } from 'element-plus'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { legalDocument } from '@/config/legal'

const route = useRoute()

const document = computed(() => {
  const kind = route.path === '/terms' ? 'terms' : 'privacy'
  return legalDocument(kind)
})
</script>

<template>
  <div class="legal-page">
    <div
      v-if="document"
      class="legal-card"
    >
      <header class="legal-header">
        <h1 class="legal-title">
          {{ document.title }}
        </h1>
        <p class="legal-updated">
          更新日期：{{ document.updated }}
        </p>
      </header>

      <p class="legal-preamble">
        {{ document.preamble }}
      </p>

      <section
        v-for="section in document.sections"
        :key="section.heading"
        class="legal-section"
      >
        <h2 class="legal-heading">
          {{ section.heading }}
        </h2>
        <p
          v-for="paragraph in section.paragraphs"
          :key="paragraph"
          class="legal-paragraph"
        >
          {{ paragraph }}
        </p>
      </section>

      <footer class="legal-footnote">
        {{ document.footnote }}
      </footer>

      <div class="legal-actions">
        <ElButton
          class="back-btn"
          @click="$router.back()"
        >
          返回
        </ElButton>
      </div>
    </div>

    <div
      v-else
      class="legal-card"
    >
      <p class="legal-paragraph">
        未找到该文档。
      </p>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// 令牌纪律（styleContract）：本页只允许 var(--*)，零裸 hex。
.legal-page {
  min-height: 100vh;
  padding: 32px 16px;
  background: var(--el-bg-color-page);
  display: flex;
  justify-content: center;
  align-items: flex-start;

  .legal-card {
    width: 100%;
    max-width: 720px;
    padding: 40px 48px;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 16px;
    box-shadow: var(--kubo-shadow-base);
  }

  .legal-header {
    margin-bottom: 16px;

    .legal-title {
      margin: 0 0 8px;
      font-size: 24px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .legal-updated {
      margin: 0;
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }
  }

  .legal-preamble {
    margin: 0 0 24px;
    font-size: 14px;
    line-height: 1.8;
    color: var(--el-text-color-regular);
  }

  .legal-section {
    margin-bottom: 20px;

    .legal-heading {
      margin: 0 0 8px;
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .legal-paragraph {
      margin: 0 0 8px;
      font-size: 14px;
      line-height: 1.8;
      color: var(--el-text-color-regular);
    }
  }

  .legal-footnote {
    margin-top: 24px;
    padding: 12px 16px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
    border-radius: 8px;
  }

  .legal-actions {
    margin-top: 24px;
    display: flex;
    justify-content: flex-start;
  }
}
</style>
