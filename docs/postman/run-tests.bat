@echo off
REM Newman 命令行测试脚本 (Windows版本)
REM 用于CI/CD流程中的自动化API测试

setlocal enabledelayedexpansion

REM 脚本配置
set SCRIPT_DIR=%~dp0
set COLLECTION_FILE=%SCRIPT_DIR%Apex-Scaffold-API.postman_collection.json
set DEV_ENV_FILE=%SCRIPT_DIR%Development.postman_environment.json
set TEST_ENV_FILE=%SCRIPT_DIR%Test.postman_environment.json
set REPORT_DIR=%SCRIPT_DIR%reports

REM 默认参数
set ENVIRONMENT=dev
set ITERATIONS=1
set DELAY=0
set REPORTERS=cli,html,json

REM 解析命令行参数
:parse_args
if "%~1"=="" goto check_newman
if "%~1"=="-e" (
    set ENVIRONMENT=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--environment" (
    set ENVIRONMENT=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="-i" (
    set ITERATIONS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--iterations" (
    set ITERATIONS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="-d" (
    set DELAY=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--delay" (
    set DELAY=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="-r" (
    set REPORTERS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="--reporters" (
    set REPORTERS=%~2
    shift
    shift
    goto parse_args
)
if "%~1"=="-h" goto print_help
if "%~1"=="--help" goto print_help
echo 错误: 未知参数 %~1
goto print_help

:print_help
echo.
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo   -e, --environment ENV    指定环境 (dev^|test)，默认: dev
echo   -i, --iterations NUM     运行次数，默认: 1
echo   -d, --delay MS          请求间隔(毫秒)，默认: 0
echo   -r, --reporters TYPE     报告类型，默认: cli,html,json
echo   -h, --help              显示帮助信息
echo.
echo 示例:
echo   %~nx0                              # 使用默认配置运行
echo   %~nx0 -e test                      # 使用测试环境
echo   %~nx0 -i 10 -d 1000                # 运行10次，间隔1秒
echo   %~nx0 -e test -r cli,html,json     # 生成多种格式报告
echo.
exit /b 0

:check_newman
REM 检查Newman是否安装
where newman >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] Newman未安装
    echo 请运行: npm install -g newman
    echo 或使用: npm install -g newman newman-reporter-htmlextra
    exit /b 1
)
echo [√] Newman已安装

:check_files
REM 检查文件是否存在
if not exist "%COLLECTION_FILE%" (
    echo [错误] 集合文件不存在: %COLLECTION_FILE%
    exit /b 1
)

if "%ENVIRONMENT%"=="dev" (
    if not exist "%DEV_ENV_FILE%" (
        echo [错误] 开发环境文件不存在: %DEV_ENV_FILE%
        exit /b 1
    )
    set ENV_FILE=%DEV_ENV_FILE%
) else if "%ENVIRONMENT%"=="test" (
    if not exist "%TEST_ENV_FILE%" (
        echo [错误] 测试环境文件不存在: %TEST_ENV_FILE%
        exit /b 1
    )
    set ENV_FILE=%TEST_ENV_FILE%
) else (
    echo [错误] 环境必须是 'dev' 或 'test'
    exit /b 1
)
echo [√] 所有文件检查通过

:create_report_dir
REM 创建报告目录
if not exist "%REPORT_DIR%" mkdir "%REPORT_DIR%"
echo [√] 报告目录已创建: %REPORT_DIR%

:run_tests
echo.
echo ==========================================
echo   开始运行 API 测试
echo ==========================================
echo 环境: %ENVIRONMENT%
echo 运行次数: %ITERATIONS%
echo 请求间隔: %DELAY%ms
echo 报告类型: %REPORTERS%
echo ==========================================
echo.

REM 生成时间戳
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /format:list') do set datetime=%%I
set TIMESTAMP=%datetime:~0,8%_%datetime:~8,6%

REM 构建Newman命令
set NEWMAN_CMD=newman run "%COLLECTION_FILE%"
set NEWMAN_CMD=%NEWMAN_CMD% -e "%ENV_FILE%"
set NEWMAN_CMD=%NEWMAN_CMD% --iteration-count %ITERATIONS%
set NEWMAN_CMD=%NEWMAN_CMD% --delay-request %DELAY%
set NEWMAN_CMD=%NEWMAN_CMD% --reporters %REPORTERS%

REM 添加HTML报告配置
echo %REPORTERS% | findstr /C:"html" >nul
if %errorlevel% equ 0 (
    set NEWMAN_CMD=!NEWMAN_CMD! --reporter-html-export "%REPORT_DIR%\report_%TIMESTAMP%.html"
)

REM 添加JSON报告配置
echo %REPORTERS% | findstr /C:"json" >nul
if %errorlevel% equ 0 (
    set NEWMAN_CMD=!NEWMAN_CMD! --reporter-json-export "%REPORT_DIR%\report_%TIMESTAMP%.json"
)

REM 添加JUnit报告配置
echo %REPORTERS% | findstr /C:"junit" >nul
if %errorlevel% equ 0 (
    set NEWMAN_CMD=!NEWMAN_CMD! --reporter-junit-export "%REPORT_DIR%\junit_%TIMESTAMP%.xml"
)

REM 执行测试
%NEWMAN_CMD%
set EXIT_CODE=%errorlevel%

echo.
echo ==========================================
if %EXIT_CODE% equ 0 (
    echo [√] 测试执行成功
    echo 报告已保存到: %REPORT_DIR%
    echo.
    echo 生成的报告文件:
    dir /b "%REPORT_DIR%\*%TIMESTAMP%*" 2>nul
) else (
    echo [×] 测试执行失败 ^(退出码: %EXIT_CODE%^)
)
echo ==========================================
echo.

exit /b %EXIT_CODE%
