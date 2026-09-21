/**
 * 内部 JSON 兼容测试的正式依赖基线：Spring Boot 3.4.4 BOM。
 * SHA-1 与 Maven Central 的 .jar.sha1 核对后，以 SHA-256 锁住测试字节。
 * 只读本地缓存，不自动安装、不从任意第三方镜像执行未核验 JAR。
 */
export const JSON_TEST_BOOT_VERSION = '3.4.4'

export const JSON_TEST_ARTIFACTS = Object.freeze([
  {
    group: 'com/fasterxml/jackson/core',
    artifact: 'jackson-annotations',
    version: '2.18.3',
    sha256: '8aa5740d80b5a5025508b41bbadbaa1fb3772267c628b2e30681a4f45f8b8931',
  },
  {
    group: 'com/fasterxml/jackson/core',
    artifact: 'jackson-core',
    version: '2.18.3',
    sha256: '056bc4d3e5e53ce821450fa97b3f9e0f8dde125cf6da6884353bb1f09582e1d9',
  },
  {
    group: 'com/fasterxml/jackson/core',
    artifact: 'jackson-databind',
    version: '2.18.3',
    sha256: '510bdda75a7a6186c5bf33b851239488a1450906ae5757121f2e1cc48a7e108f',
  },
  {
    group: 'org/projectlombok',
    artifact: 'lombok',
    version: '1.18.36',
    sha256: '73b6b05b6a2d365b700bab08d30f94de9d336490bc0acce5b6181fef48cbf18e',
  },
])
