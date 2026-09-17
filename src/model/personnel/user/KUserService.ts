import { AbstractCurdService } from '@/framework'
import { UserEntity } from './UserEntity'

/**
 * # 用户服务（新底座）
 *
 * 只覆盖**用户管理页**需要的 CRUD（`getPage` / `add` / `update` / `delete` / `enable` / `disable`）。
 *
 * 认证相关动作（`login` / `logout` / `getMyInfo` / `updateMyPassword` …）留在旧
 * `UserService` 里：调用方是 `stores/user.ts`、`component/user/*`、登录页，
 * 它们要等 F5 拆伞时统一迁移 —— 那份实现读的是 airpower 的请求管道，
 * 现在把它并过来只会让批 3 的边界变模糊。
 */
export class KUserService extends AbstractCurdService<UserEntity> {
  entityClass = UserEntity
  baseUrl = 'user'
}
