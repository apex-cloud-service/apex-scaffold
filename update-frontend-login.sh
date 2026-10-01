#!/bin/bash
# 更新前端登录页面,添加钉钉、飞书、企业微信登录按钮

FRONTEND_LOGIN_FILE="/Users/work/apex-admin-web/src/views/login/index.vue"

# 备份原文件
cp "$FRONTEND_LOGIN_FILE" "$FRONTEND_LOGIN_FILE.backup"

# 使用 sed 更新登录页面
# 1. 将 github-icon 改为通用的 social-icon
sed -i '' 's/class="github-icon"/class="social-icon"/' "$FRONTEND_LOGIN_FILE"

# 2. 在 GitHub 登录按钮后添加钉钉、飞书、企业微信登录按钮
cat > /tmp/social_login_buttons.txt << 'EOF'
        </el-tooltip>
        <el-tooltip content="钉钉登录" placement="top">
          <svg class="social-icon dingtalk-icon" viewBox="0 0 1024 1024" @click="handleSocialLogin('dingtalk')">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m244.5 337.7c-23.1 0-41.8 18.8-41.8 41.8 0 23.1 18.8 41.8 41.8 41.8s41.8-18.8 41.8-41.8c0-23-18.7-41.8-41.8-41.8z m-489 0c-23.1 0-41.8 18.8-41.8 41.8 0 23.1 18.8 41.8 41.8 41.8s41.8-18.8 41.8-41.8c0-23-18.7-41.8-41.8-41.8z m244.5 292c-77.2 0-139.7-62.5-139.7-139.7s62.5-139.7 139.7-139.7 139.7 62.5 139.7 139.7-62.5 139.7-139.7 139.7z m0-223.5c-46.3 0-83.8 37.5-83.8 83.8s37.5 83.8 83.8 83.8 83.8-37.5 83.8-83.8-37.5-83.8-83.8-83.8z" fill="#1296db"/>
          </svg>
        </el-tooltip>
        <el-tooltip content="飞书登录" placement="top">
          <svg class="social-icon feishu-icon" viewBox="0 0 1024 1024" @click="handleSocialLogin('feishu')">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m0 736c-159.1 0-288-128.9-288-288s128.9-288 288-288 288 128.9 288 288-128.9 288-288 288z" fill="#00d6b9"/>
            <path d="M512 288c-123.7 0-224 100.3-224 224s100.3 224 224 224 224-100.3 224-224-100.3-224-224-224z m0 384c-88.4 0-160-71.6-160-160s71.6-160 160-160 160 71.6 160 160-71.6 160-160 160z" fill="#00d6b9"/>
          </svg>
        </el-tooltip>
        <el-tooltip content="企业微信登录" placement="top">
          <svg class="social-icon wecom-icon" viewBox="0 0 1024 1024" @click="handleSocialLogin('wecom')">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64z m213.3 572.3c-7.1 1.4-11.3-6.9-6.4-12.4 57.8-64.7 81.7-141.1 42.8-213.8-36.2-67.6-116.3-99.8-192.3-77.4-76 22.4-123.5 94-113.8 171.6 9.7 77.5 70.6 136.5 146.2 141.5 75.6 5 141.1-36.2 157.3-107.4 1.4-6.1 8.5-9.3 13.5-6.1 5 3.2 6.4 9.7 3.2 14.7-38.3 76-123.5 120.2-213.8 110.5-90.2-9.7-160.2-80.3-169.9-169.9-9.7-89.6 51.7-168.7 136.5-186.2 84.8-17.5 169.9 31.2 201.1 115.3 31.2 84.1 3.2 173.4-85.5 219.6z" fill="#4fc3f7"/>
          </svg>
EOF

# 找到 GitHub 图标结束标签的行号,并在其后插入新按钮
awk '
  /\/svg>/{
    if (!inserted && /handleSocialLogin\(.*github/) {
      print
      while ((getline line < "/tmp/social_login_buttons.txt") > 0) {
        print line
      }
      close("/tmp/social_login_buttons.txt")
      inserted = 1
      next
    }
  }
  {print}
' "$FRONTEND_LOGIN_FILE.backup" > "$FRONTEND_LOGIN_FILE"

# 3. 更新 CSS 样式,将 github-icon 改为通用的 social-icon
cat >> "$FRONTEND_LOGIN_FILE" << 'EOF'

/* 社交登录图标通用样式 */
.social-icon {
  width: 40px;
  height: 40px;
  padding: 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.3s;
  fill: var(--el-text-color-primary);
}

.social-icon:hover {
  border-color: var(--el-color-primary);
  transform: scale(1.1);
}

/* 钉钉图标特定样式 */
.dingtalk-icon:hover {
  border-color: #1296db;
}

/* 飞书图标特定样式 */
.feishu-icon:hover {
  border-color: #00d6b9;
}

/* 企业微信图标特定样式 */
.wecom-icon:hover {
  border-color: #4fc3f7;
}
EOF

# 删除旧的 github-icon 样式
sed -i '' '/^\.github-icon {/,/^}/d' "$FRONTEND_LOGIN_FILE"

echo "✅ 前端登录页面更新完成"
echo "已添加钉钉、飞书、企业微信登录按钮"
