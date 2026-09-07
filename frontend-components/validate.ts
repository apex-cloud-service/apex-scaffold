/**
 * 表单校验规则工具
 * 
 * 提供常用的表单字段校验规则,用于Element Plus表单验证
 * 
 * 使用方式:
 * 将此文件复制到: /Users/work/apex-admin-web/src/utils/validate.ts
 */

// 手机号校验
export const validatePhone = (rule: any, value: string, callback: Function) => {
  if (!value) {
    callback(new Error('请输入手机号'))
  } else if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号格式'))
  } else {
    callback()
  }
}

// 邮箱校验
export const validateEmail = (rule: any, value: string, callback: Function) => {
  if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

// 密码强度校验
export const validatePassword = (rule: any, value: string, callback: Function) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (value.length < 8) {
    callback(new Error('密码长度至少8位'))
  } else if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(value)) {
    callback(new Error('密码必须包含大小写字母和数字'))
  } else {
    callback()
  }
}

// URL格式校验
export const validateURL = (rule: any, value: string, callback: Function) => {
  if (value && !/^https?:\/\/.+/.test(value)) {
    callback(new Error('请输入正确的URL格式(http://或https://)'))
  } else {
    callback()
  }
}

// 端口号校验
export const validatePort = (rule: any, value: number, callback: Function) => {
  if (value && (value < 1 || value > 65535)) {
    callback(new Error('端口号范围: 1-65535'))
  } else {
    callback()
  }
}

// 中文姓名校验
export const validateChineseName = (rule: any, value: string, callback: Function) => {
  if (value && !/^[\u4e00-\u9fa5]{2,4}$/.test(value)) {
    callback(new Error('请输入2-4个汉字'))
  } else {
    callback()
  }
}

// 身份证号校验
export const validateIDCard = (rule: any, value: string, callback: Function) => {
  if (value && !/^\d{17}[\dXx]$/.test(value)) {
    callback(new Error('请输入正确的身份证号'))
  } else {
    callback()
  }
}

// IP地址校验
export const validateIP = (rule: any, value: string, callback: Function) => {
  if (value && !/^(\d{1,3}\.){3}\d{1,3}$/.test(value)) {
    callback(new Error('请输入正确的IP地址格式'))
  } else {
    callback()
  }
}

// 整数校验
export const validateInteger = (rule: any, value: any, callback: Function) => {
  if (value !== '' && value !== null && !Number.isInteger(Number(value))) {
    callback(new Error('请输入整数'))
  } else {
    callback()
  }
}

// 正整数校验
export const validatePositiveInteger = (rule: any, value: any, callback: Function) => {
  const num = Number(value)
  if (value !== '' && value !== null && (!Number.isInteger(num) || num <= 0)) {
    callback(new Error('请输入正整数'))
  } else {
    callback()
  }
}
