import { AbstractBaseService } from '@/base/AbstractBaseService'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { MenuEntity } from '@/model/system/menu/MenuEntity'

/**
 * # 用户服务接口
 */
export class UserService extends AbstractBaseService<UserEntity> {
  baseUrl = 'user'

  entityClass = UserEntity

  async logout() {
    await this.api('logout').request()
  }

  /**
   * ### App登录
   * @param user 用户
   */
  async login(user: UserEntity): Promise<string> {
    const result = await this.api('login').requestRaw(user)
    return result as unknown as string
  }

  /**
   * ### 发送验证码
   * @param user 用户
   */
  async sendEmail(user: UserEntity): Promise<void> {
    await this.api('sendEmail').setTimeout(10000).request(user)
  }

  /**
   * ### 邮箱验证码登录
   * @param user 用户
   */
  async loginViaEmail(user: UserEntity): Promise<string> {
    const result = await this.api('loginViaEmail').requestRaw(user)
    return result as unknown as string
  }

  /**
   * ### 重置密码
   * @param user 用户
   */
  async resetMyPassword(user: UserEntity): Promise<void> {
    await this.api('resetMyPassword').request(user)
  }

  /**
   * ### 修改密码
   * @param user 用户
   */
  async updateMyPassword(user: UserEntity): Promise<void> {
    await this.api('updateMyPassword').request(user)
  }

  /**
   * ### 获取我有权限的菜单列表
   */
  async getMyMenuList(): Promise<MenuEntity[]> {
    return this.api('getMyMenuList').requestModelList(undefined, MenuEntity)
  }

  /**
   * ### 获取我的权限列表
   */
  async getMyPermissionList(): Promise<string[]> {
    const arr = await this.api('getMyPermissionList').requestRaw()
    return arr as string[]
  }

  /**
   * ### 获取我的个人信息
   */
  async getMyInfo(): Promise<UserEntity> {
    return this.api('getMyInfo').requestModel(undefined, UserEntity)
  }

  async updateMyInfo(user: UserEntity) {
    await this.api('updateMyInfo').request(user)
  }
}
