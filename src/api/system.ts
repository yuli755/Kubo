import type { ChangePasswordRequest, DepartmentEntity, DictionaryEntity, FileUploadResponse, LoginRequest, LoginResponse, PageRequest, PageResponse, PermissionEntity, RefreshTokenRequest, RefreshTokenResponse, RoleEntity, UserEntity } from './types'
import { request } from './axios'

// 认证相关 API
export const authApi = {
  // 登录
  login(data: LoginRequest): Promise<LoginResponse> {
    return request.post('/auth/login', data)
  },

  // 邮箱验证码登录
  loginViaEmail(data: { email: string, code: string }): Promise<LoginResponse> {
    return request.post('/auth/login/email', data)
  },

  // 发送邮箱验证码
  sendEmailCode(email: string): Promise<void> {
    return request.post('/auth/code/email', { email })
  },

  // 登出
  logout(): Promise<void> {
    return request.post('/auth/logout')
  },

  // 刷新 Token
  refreshToken(data: RefreshTokenRequest): Promise<RefreshTokenResponse> {
    return request.post('/auth/refresh', data)
  },

  // 获取当前用户信息
  getCurrentUser(): Promise<UserEntity> {
    return request.get('/auth/me')
  },

  // 修改密码
  changePassword(data: ChangePasswordRequest): Promise<void> {
    return request.post('/auth/password', data)
  },

  // 获取验证码
  getCaptcha(): Promise<{ captchaId: string, image: string }> {
    return request.get('/auth/captcha')
  },
}

// 用户管理 API
export const userApi = {
  // 分页查询
  getPage(params: PageRequest): Promise<PageResponse<UserEntity>> {
    return request.get('/system/user/page', { params })
  },

  // 获取详情
  getDetail(id: number): Promise<UserEntity> {
    return request.get(`/system/user/${id}`)
  },

  // 新增
  add(data: Partial<UserEntity>): Promise<UserEntity> {
    return request.post('/system/user', data)
  },

  // 修改
  update(data: Partial<UserEntity>): Promise<UserEntity> {
    return request.put('/system/user', data)
  },

  // 删除
  delete(ids: number[]): Promise<void> {
    return request.delete('/system/user', { params: { ids } })
  },

  // 发布/取消发布
  publish(ids: number[]): Promise<void> {
    return request.post('/system/user/publish', { ids })
  },

  // 重置密码
  resetPassword(id: number, password: string): Promise<void> {
    return request.post(`/system/user/${id}/password`, { password })
  },

  // 分配角色
  assignRoles(userId: number, roleIds: number[]): Promise<void> {
    return request.post(`/system/user/${userId}/roles`, { roleIds })
  },

  // 导出
  export(params: PageRequest): Promise<Blob> {
    return request.get('/system/user/export', { params, responseType: 'blob' })
  },
}

// 角色管理 API
export const roleApi = {
  // 分页查询
  getPage(params: PageRequest): Promise<PageResponse<RoleEntity>> {
    return request.get('/system/role/page', { params })
  },

  // 获取详情
  getDetail(id: number): Promise<RoleEntity> {
    return request.get(`/system/role/${id}`)
  },

  // 新增
  add(data: Partial<RoleEntity>): Promise<RoleEntity> {
    return request.post('/system/role', data)
  },

  // 修改
  update(data: Partial<RoleEntity>): Promise<RoleEntity> {
    return request.put('/system/role', data)
  },

  // 删除
  delete(ids: number[]): Promise<void> {
    return request.delete('/system/role', { params: { ids } })
  },

  // 获取角色权限
  getPermissions(roleId: number): Promise<number[]> {
    return request.get(`/system/role/${roleId}/permissions`)
  },

  // 分配权限
  assignPermissions(roleId: number, permissionIds: number[]): Promise<void> {
    return request.post(`/system/role/${roleId}/permissions`, { permissionIds })
  },

  // 获取所有角色 (用于下拉选择)
  getAll(): Promise<RoleEntity[]> {
    return request.get('/system/role/all')
  },
}

// 部门管理 API
export const departmentApi = {
  // 获取树形列表
  getTree(): Promise<DepartmentEntity[]> {
    return request.get('/system/department/tree')
  },

  // 分页查询
  getPage(params: PageRequest): Promise<PageResponse<DepartmentEntity>> {
    return request.get('/system/department/page', { params })
  },

  // 获取详情
  getDetail(id: number): Promise<DepartmentEntity> {
    return request.get(`/system/department/${id}`)
  },

  // 新增
  add(data: Partial<DepartmentEntity>): Promise<DepartmentEntity> {
    return request.post('/system/department', data)
  },

  // 修改
  update(data: Partial<DepartmentEntity>): Promise<DepartmentEntity> {
    return request.put('/system/department', data)
  },

  // 删除
  delete(ids: number[]): Promise<void> {
    return request.delete('/system/department', { params: { ids } })
  },

  // 获取所有部门 (用于下拉选择)
  getAll(): Promise<DepartmentEntity[]> {
    return request.get('/system/department/all')
  },
}

// 字典管理 API
export const dictionaryApi = {
  // 获取树形列表
  getTree(): Promise<DictionaryEntity[]> {
    return request.get('/system/dictionary/tree')
  },

  // 分页查询
  getPage(params: PageRequest): Promise<PageResponse<DictionaryEntity>> {
    return request.get('/system/dictionary/page', { params })
  },

  // 获取详情
  getDetail(id: number): Promise<DictionaryEntity> {
    return request.get(`/system/dictionary/${id}`)
  },

  // 新增
  add(data: Partial<DictionaryEntity>): Promise<DictionaryEntity> {
    return request.post('/system/dictionary', data)
  },

  // 修改
  update(data: Partial<DictionaryEntity>): Promise<DictionaryEntity> {
    return request.put('/system/dictionary', data)
  },

  // 删除
  delete(ids: number[]): Promise<void> {
    return request.delete('/system/dictionary', { params: { ids } })
  },

  // 根据类型获取字典项
  getByType(type: string): Promise<DictionaryEntity[]> {
    return request.get(`/system/dictionary/type/${type}`)
  },
}

// 权限管理 API
export const permissionApi = {
  // 获取树形列表
  getTree(): Promise<PermissionEntity[]> {
    return request.get('/system/permission/tree')
  },

  // 分页查询
  getPage(params: PageRequest): Promise<PageResponse<PermissionEntity>> {
    return request.get('/system/permission/page', { params })
  },

  // 获取详情
  getDetail(id: number): Promise<PermissionEntity> {
    return request.get(`/system/permission/${id}`)
  },

  // 新增
  add(data: Partial<PermissionEntity>): Promise<PermissionEntity> {
    return request.post('/system/permission', data)
  },

  // 修改
  update(data: Partial<PermissionEntity>): Promise<PermissionEntity> {
    return request.put('/system/permission', data)
  },

  // 删除
  delete(ids: number[]): Promise<void> {
    return request.delete('/system/permission', { params: { ids } })
  },

  // 获取所有权限 (用于下拉选择)
  getAll(): Promise<PermissionEntity[]> {
    return request.get('/system/permission/all')
  },
}

// 菜单管理 API
export const menuApi = {
  // 获取用户菜单树
  getUserMenus(): Promise<PermissionEntity[]> {
    return request.get('/system/menu/user')
  },

  // 获取所有菜单树
  getTree(): Promise<PermissionEntity[]> {
    return request.get('/system/menu/tree')
  },

  // 分页查询
  getPage(params: PageRequest): Promise<PageResponse<PermissionEntity>> {
    return request.get('/system/menu/page', { params })
  },

  // 获取详情
  getDetail(id: number): Promise<PermissionEntity> {
    return request.get(`/system/menu/${id}`)
  },

  // 新增
  add(data: Partial<PermissionEntity>): Promise<PermissionEntity> {
    return request.post('/system/menu', data)
  },

  // 修改
  update(data: Partial<PermissionEntity>): Promise<PermissionEntity> {
    return request.put('/system/menu', data)
  },

  // 删除
  delete(ids: number[]): Promise<void> {
    return request.delete('/system/menu', { params: { ids } })
  },
}

// 文件上传 API
export const fileApi = {
  // 单文件上传
  upload(file: File, onProgress?: (progress: number) => void): Promise<FileUploadResponse> {
    return request.upload('/file/upload', file, onProgress)
  },

  // 批量上传
  uploadBatch(files: File[], onProgress?: (progress: number) => void): Promise<FileUploadResponse[]> {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    return request.post('/file/upload/batch', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          onProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total))
        }
      },
    })
  },

  // 删除文件
  delete(id: number): Promise<void> {
    return request.delete(`/file/${id}`)
  },

  // 下载文件
  download(id: number, filename?: string): Promise<void> {
    return request.download(`/file/${id}/download`, {}, filename)
  },

  // 预览文件
  preview(id: number): Promise<string> {
    return request.get(`/file/${id}/preview`)
  },
}

// 系统配置 API
export const configApi = {
  // 获取配置列表
  getList(): Promise<Record<string, string>> {
    return request.get('/system/config')
  },

  // 获取配置详情
  getConfig(key: string): Promise<string> {
    return request.get(`/system/config/${key}`)
  },

  // 更新配置
  updateConfig(key: string, value: string): Promise<void> {
    return request.put(`/system/config/${key}`, { value })
  },

  // 批量更新
  batchUpdate(configs: Record<string, string>): Promise<void> {
    return request.put('/system/config/batch', configs)
  },
}

// 操作日志 API
export const logApi = {
  // 分页查询
  getPage(params: PageRequest): Promise<PageResponse<LogEntity>> {
    return request.get('/system/log/page', { params })
  },

  // 获取详情
  getDetail(id: number): Promise<LogEntity> {
    return request.get(`/system/log/${id}`)
  },

  // 导出
  export(params: PageRequest): Promise<Blob> {
    return request.get('/system/log/export', { params, responseType: 'blob' })
  },

  // 清理日志
  clean(days: number): Promise<void> {
    return request.delete('/system/log/clean', { params: { days } })
  },
}

// 日志实体类型
export interface LogEntity extends BaseEntity {
  userId: number
  username: string
  operation: string
  method: string
  url: string
  ip: string
  userAgent: string
  requestParams: string
  responseData: string
  status: number
  errorMessage: string
  costTime: number
}

// 导出所有 API
export const api = {
  auth: authApi,
  user: userApi,
  role: roleApi,
  department: departmentApi,
  dictionary: dictionaryApi,
  permission: permissionApi,
  menu: menuApi,
  file: fileApi,
  config: configApi,
  log: logApi,
}
