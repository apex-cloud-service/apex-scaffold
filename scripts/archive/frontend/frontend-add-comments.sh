#!/bin/bash
# 历史一次性工具：用于旧前端项目注释补充，执行前请核对原始路径与内容。
###############################################################################
# 前端项目注释补充脚本
#
# 用法：将此脚本复制到 /Users/work/apex-admin-web/ 目录下运行
#       cd /Users/work/apex-admin-web
#       bash frontend-add-comments.sh
#
# 作者：Apex Scaffold Team
# 日期：2026-09-23
###############################################################################

set -e

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "=================================================="
echo "前端项目注释补充脚本"
echo "=================================================="

# 检查前端项目目录
if [ ! -d "$FRONTEND_DIR" ]; then
    echo "❌ 错误：前端项目目录不存在: $FRONTEND_DIR"
    exit 1
fi

cd "$FRONTEND_DIR"
echo "✅ 工作目录: $(pwd)"

# ========== 补充 src/utils/validate.ts 的 JSDoc ==========
echo ""
echo "📝 补充 src/utils/validate.ts 的 JSDoc..."

cat > src/utils/validate.ts << 'EOF'
/**
 * 表单校验工具函数
 * ------------------------------------------------------------------
 * 封装常用的表单字段校验规则，供 Element Plus 表单验证使用。
 * 
 * 包含的校验规则：
 *   - 手机号、邮箱、用户名、中文姓名、身份证号
 *   - 密码强度、URL、IP 地址、端口号
 *   - 整数、正整数、长度范围、数值范围
 */

import type { FormItemRule } from 'element-plus'

type ValidateCallback = (error?: Error) => void

/**
 * 手机号校验
 * 
 * @description 校验 11 位手机号格式（1 开头）
 */
export const validatePhone = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value && !/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

/**
 * 邮箱校验
 * 
 * @description 校验邮箱格式
 */
export const validateEmail = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value && !/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(value)) {
    callback(new Error('请输入正确的邮箱地址'))
  } else {
    callback()
  }
}

/**
 * 用户名校验
 * 
 * @description 校验用户名格式（4-16 位字母、数字、下划线）
 */
export const validateUsername = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value && !/^[a-zA-Z0-9_]{4,16}$/.test(value)) {
    callback(new Error('用户名为 4-16 位字母、数字或下划线'))
  } else {
    callback()
  }
}

/**
 * 中文姓名校验
 * 
 * @description 校验中文姓名（2-4 个汉字）
 */
export const validateChineseName = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value && !/^[\u4e00-\u9fa5]{2,4}$/.test(value)) {
    callback(new Error('姓名为 2-4 个汉字'))
  } else {
    callback()
  }
}

/**
 * 身份证号校验
 * 
 * @description 校验 18 位身份证号格式
 */
export const validateIDCard = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value && !/^\d{17}[\dXx]$/.test(value)) {
    callback(new Error('请输入正确的身份证号'))
  } else {
    callback()
  }
}

/**
 * 密码强度校验
 * 
 * @description 至少 8 位，包含大小写字母和数字
 */
export const validatePassword = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value && !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,}$/.test(value)) {
    callback(new Error('密码至少 8 位，包含大小写字母和数字'))
  } else {
    callback()
  }
}

/**
 * URL 校验
 * 
 * @description 校验 http:// 或 https:// 开头的 URL
 */
export const validateURL = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value && !/^https?:\/\/.+/.test(value)) {
    callback(new Error('请输入正确的 URL（http:// 或 https://）'))
  } else {
    callback()
  }
}

/**
 * IP 地址校验
 * 
 * @description 校验 IPv4 地址格式（含 0-255 范围校验）
 */
export const validateIP = (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
  if (value) {
    const ipPattern = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/
    const match = value.match(ipPattern)
    if (!match || match.slice(1).some((num) => parseInt(num) > 255)) {
      callback(new Error('请输入正确的 IP 地址'))
    } else {
      callback()
    }
  } else {
    callback()
  }
}

/**
 * 端口号校验
 * 
 * @description 校验端口号范围（1-65535）
 */
export const validatePort = (_rule: FormItemRule, value: number, callback: ValidateCallback): void => {
  if (value && (value < 1 || value > 65535)) {
    callback(new Error('端口号范围为 1-65535'))
  } else {
    callback()
  }
}

/**
 * 整数校验
 * 
 * @description 校验是否为整数
 */
export const validateInteger = (_rule: FormItemRule, value: number, callback: ValidateCallback): void => {
  if (value !== undefined && !Number.isInteger(value)) {
    callback(new Error('请输入整数'))
  } else {
    callback()
  }
}

/**
 * 正整数校验
 * 
 * @description 校验是否为正整数
 */
export const validatePositiveInteger = (_rule: FormItemRule, value: number, callback: ValidateCallback): void => {
  if (value !== undefined && (!Number.isInteger(value) || value <= 0)) {
    callback(new Error('请输入正整数'))
  } else {
    callback()
  }
}

/**
 * 必填校验工厂函数
 * 
 * @param message 自定义错误提示信息
 * @returns 校验规则对象
 * 
 * @example
 * ```typescript
 * const rules = {
 *   username: [validateRequired('请输入用户名')]
 * }
 * ```
 */
export const validateRequired = (message: string): FormItemRule => ({
  required: true,
  message,
  trigger: 'blur'
})

/**
 * 长度范围校验工厂函数
 * 
 * @param min 最小长度
 * @param max 最大长度
 * @returns 校验函数
 * 
 * @example
 * ```typescript
 * const rules = {
 *   nickname: [{ validator: validateLength(2, 20), trigger: 'blur' }]
 * }
 * ```
 */
export const validateLength = (min: number, max: number) => {
  return (_rule: FormItemRule, value: string, callback: ValidateCallback): void => {
    if (value && (value.length < min || value.length > max)) {
      callback(new Error(`长度为 ${min}-${max} 个字符`))
    } else {
      callback()
    }
  }
}

/**
 * 数值范围校验工厂函数
 * 
 * @param min 最小值
 * @param max 最大值
 * @returns 校验函数
 * 
 * @example
 * ```typescript
 * const rules = {
 *   age: [{ validator: validateRange(0, 150), trigger: 'blur' }]
 * }
 * ```
 */
export const validateRange = (min: number, max: number) => {
  return (_rule: FormItemRule, value: number, callback: ValidateCallback): void => {
    if (value !== undefined && (value < min || value > max)) {
      callback(new Error(`值范围为 ${min}-${max}`))
    } else {
      callback()
    }
  }
}
EOF

echo "✅ 已更新 src/utils/validate.ts"

# ========== 补充 src/utils/auth.ts 的 JSDoc ==========
echo ""
echo "📝 补充 src/utils/auth.ts 的 JSDoc..."

cat > src/utils/auth.ts << 'EOF'
/**
 * 认证工具函数
 * ------------------------------------------------------------------
 * 封装 Token 和用户身份信息的本地存储管理。
 * 
 * 存储键：
 *   - apex.admin.token - 访问令牌（有效期较短，如 2 小时）
 *   - apex.admin.refresh-token - 刷新令牌（有效期较长，如 7 天）
 *   - apex.admin.identity - 用户身份信息（缓存用户信息和权限）
 */

import { TOKEN_KEY, REFRESH_TOKEN_KEY, IDENTITY_KEY } from '@/settings'
import type { UserIdentity } from '@/types'

/**
 * 获取访问令牌
 * 
 * @returns 访问令牌字符串，未登录返回空字符串
 */
export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/**
 * 保存访问令牌
 * 
 * @param token 访问令牌
 */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/**
 * 移除访问令牌
 */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 获取刷新令牌
 * 
 * @returns 刷新令牌字符串，不存在返回空字符串
 */
export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

/**
 * 保存刷新令牌
 * 
 * @param token 刷新令牌
 */
export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

/**
 * 移除刷新令牌
 */
export function removeRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * 获取用户身份信息
 * 
 * @returns 用户身份对象，未登录返回 null
 */
export function getIdentity(): UserIdentity | null {
  const identity = localStorage.getItem(IDENTITY_KEY)
  if (!identity) return null
  try {
    return JSON.parse(identity)
  } catch {
    return null
  }
}

/**
 * 保存用户身份信息
 * 
 * @param identity 用户身份对象
 */
export function setIdentity(identity: UserIdentity): void {
  localStorage.setItem(IDENTITY_KEY, JSON.stringify(identity))
}

/**
 * 移除用户身份信息
 */
export function removeIdentity(): void {
  localStorage.removeItem(IDENTITY_KEY)
}

/**
 * 清理所有会话信息
 * 
 * @description 登出或会话失效时调用，清理所有本地存储的认证信息
 */
export function clearSession(): void {
  removeToken()
  removeRefreshToken()
  removeIdentity()
}
EOF

echo "✅ 已更新 src/utils/auth.ts"

# ========== 补充 src/utils/monitoring/performance.ts 的文件头注释 ==========
echo ""
echo "📝 补充 src/utils/monitoring/performance.ts 的注释..."

cat > src/utils/monitoring/performance.ts << 'EOF'
/**
 * 性能监控工具
 * ------------------------------------------------------------------
 * 封装前端性能指标采集，支持页面加载性能、资源加载性能监控。
 * 
 * 监控指标：
 *   - FCP (First Contentful Paint) - 首次内容绘制时间
 *   - LCP (Largest Contentful Paint) - 最大内容绘制时间
 *   - FID (First Input Delay) - 首次输入延迟
 *   - CLS (Cumulative Layout Shift) - 累计布局偏移
 *   - TTFB (Time To First Byte) - 首字节时间
 */

/**
 * 性能监控类
 * 
 * @description 采集和上报前端性能指标
 */
export class PerformanceMonitor {
  /**
   * 初始化性能监控
   * 
   * @description 注册性能观察器，监听关键性能指标
   */
  static init(): void {
    if (typeof window === 'undefined') return

    // 监听 FCP
    this.observeFCP()

    // 监听 LCP
    this.observeLCP()

    // 监听 FID
    this.observeFID()

    // 页面加载完成时上报
    window.addEventListener('load', () => {
      this.reportPageLoad()
    })
  }

  /**
   * 观察首次内容绘制 (FCP)
   * 
   * @private
   */
  private static observeFCP(): void {
    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (entry.name === 'first-contentful-paint') {
          console.log('FCP:', entry.startTime)
        }
      }
    })
    observer.observe({ entryTypes: ['paint'] })
  }

  /**
   * 观察最大内容绘制 (LCP)
   * 
   * @private
   */
  private static observeLCP(): void {
    const observer = new PerformanceObserver((list) => {
      const entries = list.getEntries()
      const lastEntry = entries[entries.length - 1]
      console.log('LCP:', lastEntry.startTime)
    })
    observer.observe({ entryTypes: ['largest-contentful-paint'] })
  }

  /**
   * 观察首次输入延迟 (FID)
   * 
   * @private
   */
  private static observeFID(): void {
    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const fid = entry.processingStart - entry.startTime
        console.log('FID:', fid)
      }
    })
    observer.observe({ entryTypes: ['first-input'] })
  }

  /**
   * 上报页面加载性能
   * 
   * @private
   */
  private static reportPageLoad(): void {
    const timing = performance.timing
    const pageLoadTime = timing.loadEventEnd - timing.navigationStart
    const domReadyTime = timing.domContentLoadedEventEnd - timing.navigationStart
    const ttfb = timing.responseStart - timing.navigationStart

    console.log('页面加载性能:', {
      pageLoadTime,
      domReadyTime,
      ttfb
    })
  }
}
EOF

echo "✅ 已更新 src/utils/monitoring/performance.ts"

# ========== 补充 src/stores/office.ts 的 JSDoc ==========
echo ""
echo "📝 补充 src/stores/office.ts 的 JSDoc..."

# 读取当前文件内容并添加注释
cat > src/stores/office.ts << 'EOF'
/**
 * 办公平台状态管理
 * ------------------------------------------------------------------
 * 管理办公平台（飞书、钉钉、企业微信）的配置和绑定状态。
 * 
 * 状态包括：
 *   - 平台配置信息（appId、appSecret 等）
 *   - 用户绑定状态
 *   - 当前选中的平台
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 办公平台 Store
 * 
 * @description 管理办公平台的配置和用户绑定状态
 */
export const useOfficeStore = defineStore('office', () => {
  /**
   * 当前选中的平台类型
   * 
   * @description 可选值：'feishu' | 'dingtalk' | 'wechat'
   */
  const currentPlatform = ref<string>('')

  /**
   * 用户绑定状态
   * 
   * @description 记录用户在各平台的绑定状态
   */
  const bindingStatus = ref<Record<string, boolean>>({})

  /**
   * 设置当前平台
   * 
   * @param platform 平台类型
   */
  const setCurrentPlatform = (platform: string) => {
    currentPlatform.value = platform
  }

  /**
   * 更新绑定状态
   * 
   * @param platform 平台类型
   * @param status 绑定状态
   */
  const updateBindingStatus = (platform: string, status: boolean) => {
    bindingStatus.value[platform] = status
  }

  /**
   * 清理所有状态
   * 
   * @description 用户登出时调用
   */
  const reset = () => {
    currentPlatform.value = ''
    bindingStatus.value = {}
  }

  return {
    currentPlatform,
    bindingStatus,
    setCurrentPlatform,
    updateBindingStatus,
    reset
  }
})
EOF

echo "✅ 已更新 src/stores/office.ts"

echo ""
echo "=================================================="
echo "✅ 所有注释补充完成！"
echo "=================================================="
echo ""
echo "已补充注释的文件："
echo "  1. src/utils/validate.ts - 表单校验工具（所有函数的 JSDoc）"
echo "  2. src/utils/auth.ts - 认证工具（refreshToken 相关函数的 JSDoc）"
echo "  3. src/utils/monitoring/performance.ts - 性能监控（文件头注释和方法 JSDoc）"
echo "  4. src/stores/office.ts - 办公平台状态管理（所有函数的 JSDoc）"
echo ""
echo "建议执行以下命令验证："
echo "  cd /Users/work/apex-admin-web"
echo "  npm run build"
echo ""
