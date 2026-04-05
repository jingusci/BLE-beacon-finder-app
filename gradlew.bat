@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
set EXIT_CODE=%ERRORLEVEL%

if %EXIT_CODE% equ 0 call :postDebugApk %*
if %EXIT_CODE% neq 0 exit /b %EXIT_CODE%
goto end

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal
goto :eof

:omega

:postDebugApk
setlocal EnableDelayedExpansion
set RUN_POST_BUILD=
for %%A in (%*) do (
    if /I "%%~A"=="assembleDebug" set RUN_POST_BUILD=1
)

if not defined RUN_POST_BUILD (
    endlocal
    goto :eof
)

set APK_PATH=%APP_HOME%\app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
    endlocal
    goto :eof
)

set VERSIONS_DIR=%APP_HOME%\versiones_apk
if not exist "%VERSIONS_DIR%" mkdir "%VERSIONS_DIR%"

set MAX_MAJOR=-1
set MAX_MINOR=0
set MAX_PATCH=0

for /d %%D in ("%VERSIONS_DIR%\v*.*.*") do (
    set CURRENT_NAME=%%~nxD
    for /f "tokens=1-3 delims=v." %%a in ("!CURRENT_NAME!") do (
        set CURRENT_MAJOR=%%a
        set CURRENT_MINOR=%%b
        set CURRENT_PATCH=%%c
    )

    if !CURRENT_MAJOR! GTR !MAX_MAJOR! (
        set MAX_MAJOR=!CURRENT_MAJOR!
        set MAX_MINOR=!CURRENT_MINOR!
        set MAX_PATCH=!CURRENT_PATCH!
    ) else if !CURRENT_MAJOR! EQU !MAX_MAJOR! (
        if !CURRENT_MINOR! GTR !MAX_MINOR! (
            set MAX_MINOR=!CURRENT_MINOR!
            set MAX_PATCH=!CURRENT_PATCH!
        ) else if !CURRENT_MINOR! EQU !MAX_MINOR! (
            if !CURRENT_PATCH! GTR !MAX_PATCH! (
                set MAX_PATCH=!CURRENT_PATCH!
            )
        )
    )
)

if !MAX_MAJOR! LSS 0 (
    set NEXT_VERSION=v0.1.0
) else (
    set /a NEXT_PATCH=MAX_PATCH+1
    set NEXT_VERSION=v!MAX_MAJOR!.!MAX_MINOR!.!NEXT_PATCH!
)

set TARGET_DIR=%VERSIONS_DIR%\%NEXT_VERSION%
if exist "%TARGET_DIR%" (
    endlocal
    goto :eof
)

mkdir "%TARGET_DIR%"
copy /Y "%APK_PATH%" "%TARGET_DIR%\app-debug.apk" >NUL
(
    echo # %NEXT_VERSION%
    echo.
    echo ## Fecha de generacion
    echo - %DATE% %TIME%
    echo.
    echo ## APK
    echo - app-debug.apk
    echo.
    echo ## Cambios respecto a la version anterior
    echo - Completar.
) > "%TARGET_DIR%\README.md"
echo APK debug copiado en "%TARGET_DIR%\app-debug.apk"
endlocal
goto :eof
