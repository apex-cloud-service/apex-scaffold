#!/bin/bash
# 历史一次性工具：用于旧前端 TypeScript 错误修复，执行前请核对原始路径与内容。
###############################################################################
# 修复前端 TypeScript 错误脚本
#
# 用法：cd /Users/work/apex-admin-web && bash fix-typescript-errors.sh
#
# 作者：Apex Scaffold Team
# 日期：2026-09-23
###############################################################################

FRONTEND_DIR="/Users/work/apex-admin-web"

echo "=================================================="
echo "修复前端 TypeScript 错误"
echo "=================================================="

cd "$FRONTEND_DIR" || exit 1
echo "✅ 工作目录: $(pwd)"

# ========== 修复 validate.spec.ts 测试文件 ==========
echo ""
echo "📝 修复 src/utils/__tests__/validate.spec.ts..."

cat > src/utils/__tests__/validate.spec.ts << 'EOF'
import { describe, it, expect } from 'vitest'
import { validateEmail, validatePhone } from '../validate'

describe('validate utils', () => {
  describe('validateEmail', () => {
    it('should validate correct email', () => {
      const callback = (error?: Error) => {
        expect(error).toBeUndefined()
      }
      validateEmail({} as any, 'test@example.com', callback)
    })

    it('should reject invalid email', () => {
      const callback = (error?: Error) => {
        expect(error).toBeDefined()
        expect(error?.message).toBe('请输入正确的邮箱地址')
      }
      validateEmail({} as any, 'invalid-email', callback)
    })
  })

  describe('validatePhone', () => {
    it('should validate correct phone', () => {
      const callback = (error?: Error) => {
        expect(error).toBeUndefined()
      }
      validatePhone({} as any, '13800138000', callback)
    })

    it('should reject invalid phone', () => {
      const callback = (error?: Error) => {
        expect(error).toBeDefined()
        expect(error?.message).toBe('请输入正确的手机号')
      }
      validatePhone({} as any, '12345', callback)
    })
  })
})
EOF

echo "✅ 已更新 src/utils/__tests__/validate.spec.ts"

# ========== 修复 performance.ts 类型错误 ==========
echo ""
echo "📝 修复 src/utils/monitoring/performance.ts 类型错误..."

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

interface PerformanceEntryWithProcessing extends PerformanceEntry {
  processingStart?: number
}

export class PerformanceMonitor {
  static init(): void {
    if (typeof window === 'undefined') return

    this.observeFCP()
    this.observeLCP()
    this.observeFID()

    window.addEventListener('load', () => {
      this.reportPageLoad()
    })
  }

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

  private static observeLCP(): void {
    const observer = new PerformanceObserver((list) => {
      const entries = list.getEntries()
      const lastEntry = entries[entries.length - 1]
      console.log('LCP:', lastEntry.startTime)
    })
    observer.observe({ entryTypes: ['largest-contentful-paint'] })
  }

  private static observeFID(): void {
    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const entryWithProcessing = entry as PerformanceEntryWithProcessing
        if (entryWithProcessing.processingStart !== undefined) {
          const fid = entryWithProcessing.processingStart - entry.startTime
          console.log('FID:', fid)
        }
      }
    })
    observer.observe({ entryTypes: ['first-input'] })
  }

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

echo ""
echo "=================================================="
echo "✅ TypeScript 错误修复完成！"
echo "=================================================="
echo ""
echo "已修复的文件："
echo "  1. src/utils/__tests__/validate.spec.ts - 测试文件函数名匹配"
echo "  2. src/utils/monitoring/performance.ts - 类型安全处理"
echo ""
echo "建议执行以下命令验证："
echo "  cd /Users/work/apex-admin-web"
echo "  npm run build"
echo ""
