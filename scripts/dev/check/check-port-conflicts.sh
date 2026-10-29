#!/usr/bin/env bash
# 用法: check-port-conflicts.sh [compose.yml ...]，默认检查主服务栈。
# 只读取配置及本机监听状态，不加载 .env，不启停服务。
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || exit
source "${SCRIPT_DIR}/../../lib/common.sh" || exit
setup_error_trap
PROJECT_ROOT=$(get_project_root)
require_command ruby
if [[ $# == 0 ]]; then
    set -- "$PROJECT_ROOT/podman-compose.yml"
fi

# 使用 Ruby 标准库解析 Compose，避免依赖完整容器运行环境。
PORTS=$(ruby - "$@" <<'RUBY'
require 'yaml'
begin
  ARGV.each do |path|
    config = YAML.safe_load(File.read(path), aliases: true)
    config.fetch('services').each do |service, settings|
      Array(settings['ports']).each do |mapping|
        unless mapping.is_a?(String)
          raise "#{path}: #{service}: 仅支持短格式端口映射"
        end
        mapping = mapping.gsub(/\$\{([A-Za-z_][A-Za-z0-9_]*)(?:(:-|-)([^}]*))?\}/) do
          key, operator, fallback = Regexp.last_match.captures
          value = ENV[key]
          value = fallback if value.nil? || (operator == ':-' && value.empty?)
          raise "未设置端口变量 #{key}" if value.nil?
          value
        end
        match = mapping.match(/(?:\A|:)([0-9]+):([0-9]+)(?:\/(tcp|udp))?\z/)
        raise "#{path}: #{service}: 不支持的端口映射" unless match
        raise "#{path}: #{service}: 仅支持 TCP 检测" if match[3] == 'udp'
        port = Integer(match[1])
        raise "#{path}: #{service}: 端口超出范围" unless (1..65535).cover?(port)
        puts [port, service, path].join("\t")
      end
    end
  end
rescue StandardError => e
  warn e.message
  exit 2
end
RUBY
) || exit 2

print_header "Compose 宿主机 TCP 端口监听报告"
echo "范围：所选配置的全部服务（含可选 profile）；环境变量覆盖默认值，不读取 .env。"
echo "仅检测本机当前用户可见监听；已占用不代表应用故障，不推断容器内端口冲突。"
if ! command -v lsof >/dev/null 2>&1; then
    printf '%s\n' "$PORTS"
    log_warn "未安装 lsof：以上仅为配置规划，未检测实际监听。"
    exit 2
fi
ERROR_FILE=$(create_temp_file apex-port-check)
setup_temp_cleanup "$ERROR_FILE"
RESULT=0
while IFS=$'\t' read -r port service config; do
    [[ -n "$port" ]] || continue
    printf '\n%s / %s / TCP %s\n' "$config" "$service" "$port"
    : > "$ERROR_FILE"
    if LISTENERS=$(lsof -nP -a -iTCP:"$port" -sTCP:LISTEN 2> "$ERROR_FILE"); then
        printf '%s\n' "$LISTENERS"
        echo "已发现监听（需确认是否为预期进程）"
        [[ $RESULT == 2 ]] || RESULT=1
    else
        STATUS=$?
        if [[ $STATUS != 1 || -s "$ERROR_FILE" ]]; then
            log_error "lsof 检测失败（退出码 ${STATUS}），无法判断监听状态"
            RESULT=2
        else
            echo "未发现可见监听（检查时刻的快照）"
        fi
    fi
    if [[ -s "$ERROR_FILE" ]]; then
        while IFS= read -r line; do log_warn "$line"; done < "$ERROR_FILE"
        RESULT=2
    fi
done <<< "$PORTS"
exit "$RESULT"
