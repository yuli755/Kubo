/**
 * Java 17 无第三方 classpath 的业务源码子集。
 * 同时被独立 javac runner 与零回潮门禁消费，避免“测的是一份、保护的是另一份”。
 * @Dictionary 约束的枚举不在此列；不生成/复制旧接口来凑出一个可编译的假环境。
 */
export const INDEPENDENT_DOMAIN_SOURCES = Object.freeze([
  'SPMS-Server/src/main/java/cn/hamm/spms/common/AppConstant.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/personnel/user/enums/UserTokenType.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/coderule/enums/CodeRuleParam.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/file/enums/FileCategory.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/file/enums/FileUploadPlatform.java',
  'SPMS-Server/src/main/java/cn/hamm/spms/module/system/permission/enums/PermissionType.java',
])
