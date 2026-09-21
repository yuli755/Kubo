/** HTTP 契约的补充正式依赖；字节已与 Maven Central 发布校验和核对。 */
import { COOKIE_TEST_ARTIFACTS } from './backend-cookie-artifacts.mjs'
import { JSON_TEST_ARTIFACTS } from './backend-json-artifacts.mjs'

const extra = [
  {
    'group': 'org/slf4j',
    'artifact': 'slf4j-api',
    'version': '2.0.17',
    'sha256': '7b751d952061954d5abfed7181c1f645d336091b679891591d63329c622eb832'
  },
  {
    'group': 'jakarta/validation',
    'artifact': 'jakarta.validation-api',
    'version': '3.0.2',
    'sha256': '291c25e6910cc6a7ebd96d4c6baebf6d7c37676c5482c2d96146e901b62c1fc9'
  },
  {
    'group': 'org/springframework',
    'artifact': 'spring-web',
    'version': '6.2.5',
    'sha256': '85f0c1acaf033a5f7abdcaf348f1dd2cebe1f77687ee77f05e8cb66bfbc6fecf'
  },
  {
    'group': 'org/springframework',
    'artifact': 'spring-webmvc',
    'version': '6.2.5',
    'sha256': 'b8d281aedf1e9cbf73bb2c6cf72d3eaf811f9298b2dc5017e9c40b3cb9284b46'
  },
  {
    'group': 'org/springframework',
    'artifact': 'spring-test',
    'version': '6.2.5',
    'sha256': '5585586296cf9778d8d027971b529746561e0d9b2953d00956b27a1f5b1f0d7e'
  },
  {
    'group': 'org/springframework',
    'artifact': 'spring-tx',
    'version': '6.2.5',
    'sha256': 'a0fc4f275e04cf1b31dde8b5348e10ed03372cd734c6e3d14e0863263794a9e0'
  }
]

export const HTTP_TEST_ARTIFACTS = Object.freeze([...new Map(
  [...COOKIE_TEST_ARTIFACTS, ...JSON_TEST_ARTIFACTS, ...extra].map(item => [`${item.group}:${item.artifact}`, item]),
).values()])

/** Maven Central 官方 core 6.3.1 JAR 的 SHA-1；源码参考模式单独显示，不能混称正式 JAR 验证。 */
export const LEGACY_CORE_SHA1 = '34faf09ef6c90e7e1cb4714418d63cef7041bb13'
