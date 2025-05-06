@echo off
setlocal enabledelayedexpansion

echo Checking PostgreSQL installation...

set "PSQL_PATH=C:\Program Files\PostgreSQL\17\bin\psql.exe"

if not exist "%PSQL_PATH%" (
    echo PostgreSQL is not found at expected location: %PSQL_PATH%
    echo Please make sure PostgreSQL is installed correctly
    pause
    exit /b 1
)

echo Found PostgreSQL at: %PSQL_PATH%

REM Set environment variables
set PGHOST=localhost
set PGUSER=postgres

REM Ask for postgres user password
set /p PGPASSWORD="Enter postgres user password: "

echo Testing connection to PostgreSQL...
"%PSQL_PATH%" -U postgres -c "SELECT 1" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Failed to connect to PostgreSQL. Please check your password and make sure PostgreSQL is running.
    pause
    exit /b 1
)

echo Connection successful!

echo Creating database and user...

REM Check if user exists
"%PSQL_PATH%" -U postgres -t -c "SELECT 1 FROM pg_roles WHERE rolname='kpok2'" | findstr /r "^[ ]*1[ ]*$" >nul
if %ERRORLEVEL% equ 0 (
    echo User kpok2 already exists
) else (
    echo Creating user kpok2...
    "%PSQL_PATH%" -U postgres -c "CREATE USER kpok2 WITH PASSWORD 'kpok2' CREATEDB;"
    if %ERRORLEVEL% neq 0 (
        echo Failed to create user
        pause
        exit /b 1
    )
)

REM Check if database exists
"%PSQL_PATH%" -U postgres -t -c "SELECT 1 FROM pg_database WHERE datname='kpok2_db'" | findstr /r "^[ ]*1[ ]*$" >nul
if %ERRORLEVEL% equ 0 (
    echo Database kpok2_db already exists
) else (
    echo Creating database kpok2_db...
    "%PSQL_PATH%" -U postgres -c "CREATE DATABASE kpok2_db OWNER kpok2;"
    if %ERRORLEVEL% neq 0 (
        echo Failed to create database
        pause
        exit /b 1
    )
)

REM Grant privileges
echo Granting privileges...
"%PSQL_PATH%" -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE kpok2_db TO kpok2;"
if %ERRORLEVEL% neq 0 (
    echo Failed to grant privileges
    pause
    exit /b 1
)

REM Test connection with new user
echo Testing connection with new user...
set PGUSER=kpok2
set PGPASSWORD=kpok2
"%PSQL_PATH%" -U kpok2 -d kpok2_db -c "SELECT 1" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Warning: Could not connect with new user. Please check permissions.
) else (
    echo Connection test with new user successful!
)

echo.
echo Database setup completed successfully!
echo.
echo Database details:
echo Host: localhost
echo Port: 5432 (default PostgreSQL port)
echo Database: kpok2_db
echo Username: kpok2
echo Password: kpok2
echo.
echo You can now start the application.
pause 