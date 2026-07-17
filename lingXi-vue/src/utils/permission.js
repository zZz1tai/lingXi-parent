import useUserStore from '@/store/modules/user'

/**
 * 简单匹配函数，支持*通配符
 * @param {string} pattern 匹配模式
 * @param {string} str 待匹配字符串
 * @returns {boolean} 是否匹配成功
 */
export function simpleMatch(pattern, str) {
  if (pattern === null || str === null) {
    return false;
  }
  const patArr = pattern.split("*");
  let strIdx = 0;
  for (let i = 0; i < patArr.length; i++) {
    const pat = patArr[i];
    if (pat === "") {
      continue;
    }
    const idx = str.indexOf(pat, strIdx);
    if (idx === -1) {
      return false;
    }
    strIdx = idx + pat.length;
  }
  return true;
}

/**
 * 字符权限校验
 * @param {Array} value 校验值
 * @returns {Boolean}
 */
export function checkPermi(value) {
  if (value && value instanceof Array && value.length > 0) {
    const permissions = useUserStore().permissions
    const permissionDatas = value
    const all_permission = "*:*:*";

    const hasPermission = permissions.some(permission => {
      return all_permission === permission || permissionDatas.some(pattern => simpleMatch(pattern, permission))
    })

    if (!hasPermission) {
      return false
    }
    return true
  } else {
    console.error(`need roles! Like checkPermi="['system:user:add','system:user:edit']"`)
    return false
  }
}

/**
 * 角色权限校验
 * @param {Array} value 校验值
 * @returns {Boolean}
 */
export function checkRole(value) {
  if (value && value instanceof Array && value.length > 0) {
    const roles = useUserStore().roles
    const permissionRoles = value
    const super_admin = "admin";

    const hasRole = roles.some(role => {
      return super_admin === role || permissionRoles.includes(role)
    })

    if (!hasRole) {
      return false
    }
    return true
  } else {
    console.error(`need roles! Like checkRole="['admin','editor']"`)
    return false
  }
}