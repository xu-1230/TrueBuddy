@echo off
echo [1/3] 正在启动 PostgreSQL...

:: 设置路径变量（注意路径分隔符）
set PG_HOME=D:/ctf/postgresql/pgsql
set PG_BIN=%PG_HOME%/pgsql/bin
set PG_DATA=%PG_HOME%/data
set PG_LOG=%PG_HOME%/logfile

:: 检查必要文件是否存在
if not exist "%PG_BIN%/pg_ctl.exe" (
    echo [×] 错误：找不到 pg_ctl.exe
    echo 请检查路径：%PG_BIN%/pg_ctl.exe
    pause
    exit /b 1
)

if not exist "%PG_DATA%" (
    echo [×] 错误：数据目录不存在
    echo 请检查路径：%PG_DATA%
    pause
    exit /b 1
)

:: 启动 PostgreSQL（关键修改：使用双引号包围路径）
echo 执行命令: "%PG_BIN%/pg_ctl.exe" -D "%PG_DATA%" -l "%PG_LOG%" start
echo.

"%PG_BIN%/pg_ctl.exe" -D "%PG_DATA%" -l "%PG_LOG%" start

:: 检查启动结果
if %errorlevel% equ 0 (
    echo [√] PostgreSQL 启动成功！
) else (
    echo [×] PostgreSQL 启动失败，错误代码: %errorlevel%
    echo 请检查日志文件: %PG_LOG%
)

echo.
echo ========================================
echo 所有服务启动完成！
echo 按任意键退出...
pause >nul