#!/bin/bash

# Newman 命令行测试脚本
# 用于CI/CD流程中的自动化API测试

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COLLECTION_FILE="$SCRIPT_DIR/Apex-Scaffold-API.postman_collection.json"
DEV_ENV_FILE="$SCRIPT_DIR/Development.postman_environment.json"
TEST_ENV_FILE="$SCRIPT_DIR/Test.postman_environment.json"
REPORT_DIR="$SCRIPT_DIR/reports"

# 打印帮助信息
print_help() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -e, --environment ENV    指定环境 (dev|test)，默认: dev"
    echo "  -i, --iterations NUM     运行次数，默认: 1"
    echo "  -d, --delay MS          请求间隔(毫秒)，默认: 0"
    echo "  -r, --reporters TYPE     报告类型，默认: cli,html,json"
    echo "  -o, --output DIR        报告输出目录，默认: ./reports"
    echo "  -h, --help              显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                                  # 使用默认配置运行"
    echo "  $0 -e test                          # 使用测试环境"
    echo "  $0 -i 10 -d 1000                    # 运行10次，间隔1秒"
    echo "  $0 -e test -r cli,html,json         # 生成多种格式报告"
}

# 检查Newman是否安装
check_newman() {
    if ! command -v newman &> /dev/null; then
        echo -e "${RED}错误: Newman未安装${NC}"
        echo "请运行: npm install -g newman"
        echo "或使用: npm install -g newman newman-reporter-htmlextra"
        exit 1
    fi
    echo -e "${GREEN}✓ Newman已安装${NC}"
}

# 检查文件是否存在
check_files() {
    if [ ! -f "$COLLECTION_FILE" ]; then
        echo -e "${RED}错误: 集合文件不存在: $COLLECTION_FILE${NC}"
        exit 1
    fi
    
    if [ "$ENVIRONMENT" = "dev" ] && [ ! -f "$DEV_ENV_FILE" ]; then
        echo -e "${RED}错误: 开发环境文件不存在: $DEV_ENV_FILE${NC}"
        exit 1
    fi
    
    if [ "$ENVIRONMENT" = "test" ] && [ ! -f "$TEST_ENV_FILE" ]; then
        echo -e "${RED}错误: 测试环境文件不存在: $TEST_ENV_FILE${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ 所有文件检查通过${NC}"
}

# 创建报告目录
create_report_dir() {
    mkdir -p "$REPORT_DIR"
    echo -e "${GREEN}✓ 报告目录已创建: $REPORT_DIR${NC}"
}

# 运行Newman测试
run_tests() {
    echo ""
    echo "=========================================="
    echo "  开始运行 API 测试"
    echo "=========================================="
    echo "环境: $ENVIRONMENT"
    echo "运行次数: $ITERATIONS"
    echo "请求间隔: ${DELAY}ms"
    echo "报告类型: $REPORTERS"
    echo "=========================================="
    echo ""
    
    # 选择环境文件
    if [ "$ENVIRONMENT" = "test" ]; then
        ENV_FILE="$TEST_ENV_FILE"
    else
        ENV_FILE="$DEV_ENV_FILE"
    fi
    
    # 生成时间戳
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    
    # 构建Newman命令
    NEWMAN_CMD="newman run \"$COLLECTION_FILE\" \
        -e \"$ENV_FILE\" \
        --iteration-count $ITERATIONS \
        --delay-request $DELAY \
        --reporters $REPORTERS"
    
    # 添加HTML报告配置
    if [[ $REPORTERS == *"html"* ]]; then
        NEWMAN_CMD="$NEWMAN_CMD \
            --reporter-html-export \"$REPORT_DIR/report_${TIMESTAMP}.html\""
    fi
    
    # 添加JSON报告配置
    if [[ $REPORTERS == *"json"* ]]; then
        NEWMAN_CMD="$NEWMAN_CMD \
            --reporter-json-export \"$REPORT_DIR/report_${TIMESTAMP}.json\""
    fi
    
    # 添加JUnit报告配置
    if [[ $REPORTERS == *"junit"* ]]; then
        NEWMAN_CMD="$NEWMAN_CMD \
            --reporter-junit-export \"$REPORT_DIR/junit_${TIMESTAMP}.xml\""
    fi
    
    # 执行测试
    eval $NEWMAN_CMD
    EXIT_CODE=$?
    
    echo ""
    echo "=========================================="
    if [ $EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}✓ 测试执行成功${NC}"
        echo "报告已保存到: $REPORT_DIR"
        
        # 列出生成的报告文件
        if [ -d "$REPORT_DIR" ]; then
            echo ""
            echo "生成的报告文件:"
            ls -lh "$REPORT_DIR" | grep "$TIMESTAMP" | awk '{print "  - " $9 " (" $5 ")"}'
        fi
    else
        echo -e "${RED}✗ 测试执行失败 (退出码: $EXIT_CODE)${NC}"
    fi
    echo "=========================================="
    echo ""
    
    return $EXIT_CODE
}

# 默认参数
ENVIRONMENT="dev"
ITERATIONS=1
DELAY=0
REPORTERS="cli,html,json"
OUTPUT_DIR="$REPORT_DIR"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -i|--iterations)
            ITERATIONS="$2"
            shift 2
            ;;
        -d|--delay)
            DELAY="$2"
            shift 2
            ;;
        -r|--reporters)
            REPORTERS="$2"
            shift 2
            ;;
        -o|--output)
            REPORT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            print_help
            exit 0
            ;;
        *)
            echo -e "${RED}错误: 未知参数 $1${NC}"
            print_help
            exit 1
            ;;
    esac
done

# 验证环境参数
if [ "$ENVIRONMENT" != "dev" ] && [ "$ENVIRONMENT" != "test" ]; then
    echo -e "${RED}错误: 环境必须是 'dev' 或 'test'${NC}"
    exit 1
fi

# 主流程
main() {
    echo ""
    echo "=========================================="
    echo "  Apex Scaffold API 测试套件"
    echo "=========================================="
    echo ""
    
    check_newman
    check_files
    create_report_dir
    run_tests
    
    exit $?
}

# 运行主流程
main
