export { departmentApi } from './department'
export { roleApi } from './role'
export { userApi } from './user'

export const personnelApi = {
  department: departmentApi,
  role: roleApi,
  user: userApi,
}