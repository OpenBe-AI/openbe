#!/usr/bin/env bash
# ============================================================
#  OpenBe 一键安装脚本  v0.5.0
#  支持：macOS (Intel / Apple Silicon) · Linux (apt / dnf / yum)
#
#  远程安装（推荐）：
#    curl -fsSL https://openbe.ai/install.sh | bash
#
#  本地源码安装：
#    bash install.sh
# ============================================================

set -eo pipefail

# ── 错误陷阱 ──────────────────────────────────────────────
trap '_err_exit $LINENO "$BASH_COMMAND"' ERR
_err_exit() {
    echo ""
    echo -e "\033[0;31m╔══════════════════════════════════════════════╗\033[0m"
    echo -e "\033[0;31m║  安装失败！                                  ║\033[0m"
    echo -e "\033[0;31m╚══════════════════════════════════════════════╝\033[0m"
    echo -e "\033[0;31m  出错行：$1\033[0m"
    echo -e "\033[0;31m  命令  ：$2\033[0m"
    echo ""
    exit 1
}

# ── 颜色 & 工具函数 ───────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; DIM='\033[2m'; RESET='\033[0m'

step()    { echo ""; echo -e "${BOLD}${CYAN}┌─ $*${RESET}"; }
ok()      { echo -e "${GREEN}└─ ✓ $*${RESET}"; }
info()    { echo -e "  ${DIM}$*${RESET}"; }
warn()    { echo -e "  ${YELLOW}⚠ $*${RESET}"; }
die()     { echo -e "  ${RED}✗ $*${RESET}" >&2; exit 1; }

# ── 安装模式检测 ──────────────────────────────────────────
# 通过 curl | bash 执行时 BASH_SOURCE[0] 为空或 /dev/stdin
SCRIPT_PATH="${BASH_SOURCE[0]:-}"
if [[ -z "${SCRIPT_PATH}" || "${SCRIPT_PATH}" == "/dev/stdin" || "${SCRIPT_PATH}" == "bash" ]]; then
    INSTALL_MODE="remote"
else
    INSTALL_MODE="local"
    WORKSPACE_DIR="$(cd "$(dirname "${SCRIPT_PATH}")" && pwd)"
fi

# ── 远程资源基础 URL ──────────────────────────────────────
RELEASE_BASE="https://github.com/OpenBe-AI/openbe/releases/latest/download"
OPENBE_HOME="${HOME}/.openbe"

# ── 操作系统检测 ──────────────────────────────────────────
OS_TYPE=""
PKG_MGR=""
case "$(uname -s)" in
    Darwin) OS_TYPE="macos" ;;
    Linux)
        OS_TYPE="linux"
        if   command -v apt-get &>/dev/null; then PKG_MGR="apt"
        elif command -v dnf     &>/dev/null; then PKG_MGR="dnf"
        elif command -v yum     &>/dev/null; then PKG_MGR="yum"
        else die "不支持的 Linux 发行版（仅支持 apt / dnf / yum）"; fi
        ;;
    *) die "不支持的操作系统：$(uname -s)" ;;
esac

# ── 确定 openbe 命令安装目录 ──────────────────────────────
if [[ "${OS_TYPE}" == "macos" ]]; then
    if   [[ -d /opt/homebrew/bin && -w /opt/homebrew/bin ]]; then OPENBE_BIN="/opt/homebrew/bin"
    elif [[ -d /usr/local/bin   && -w /usr/local/bin   ]]; then OPENBE_BIN="/usr/local/bin"
    else OPENBE_BIN="${HOME}/.local/bin"; fi
else
    if [[ -d /usr/local/bin && -w /usr/local/bin ]]; then OPENBE_BIN="/usr/local/bin"
    else OPENBE_BIN="${HOME}/.local/bin"; fi
fi

# ============================================================
# Banner
# ============================================================
echo -e "${YELLOW}"
cat << 'BANNER'
 ██████╗ ██████╗ ███████╗███╗   ██╗██████╗ ███████╗
██╔═══██╗██╔══██╗██╔════╝████╗  ██║██╔══██╗██╔════╝
██║   ██║██████╔╝█████╗  ██╔██╗ ██║██████╔╝█████╗
██║   ██║██╔═══╝ ██╔══╝  ██║╚██╗██║██╔══██╗██╔══╝
╚██████╔╝██║     ███████╗██║ ╚████║██████╔╝███████╗
 ╚═════╝ ╚═╝     ╚══════╝╚═╝  ╚═══╝╚═════╝ ╚══════╝
BANNER
echo -e "${RESET}"
echo -e "${BOLD}  OpenBe 安装程序 v0.5.0${RESET}"
echo -e "${DIM}  平台：${OS_TYPE}  |  模式：${INSTALL_MODE}  |  命令目录：${OPENBE_BIN}${RESET}"
echo ""

# ============================================================
# Step 1 — 自动安装缺失依赖
# ============================================================
step "Step 1/4  检查 & 自动安装依赖"

# ── Java 版本检测辅助 ─────────────────────────────────────
_check_java_version() {
    hash -r 2>/dev/null || true
    local ver_str; ver_str=$(java -version 2>&1 | head -1 || true)
    echo "${ver_str}" | sed -E 's/.*"([0-9]+)(\..*)?".*/\1/'
}

# ──────────────────────────────────────────────────────────
# macOS 依赖
# ──────────────────────────────────────────────────────────
if [[ "${OS_TYPE}" == "macos" ]]; then

    echo -e "  检查 Homebrew..."
    if ! command -v brew &>/dev/null; then
        echo -e "  ${YELLOW}→ 未找到 Homebrew，正在安装...${RESET}"
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        [[ -f /opt/homebrew/bin/brew ]] && eval "$(/opt/homebrew/bin/brew shellenv)"
        [[ -f /usr/local/bin/brew ]]    && eval "$(/usr/local/bin/brew shellenv)"
    fi
    command -v brew &>/dev/null || die "Homebrew 安装失败，请手动安装：https://brew.sh"
    info "Homebrew $(brew --version | head -1 || true)"

    BREW_PREFIX="$(brew --prefix)"
    export PATH="${BREW_PREFIX}/opt/openjdk@21/bin:${BREW_PREFIX}/opt/openjdk/bin:${PATH}"

    echo -e "  检查 Java..."
    _need_java=false
    if ! command -v java &>/dev/null 2>&1; then
        _need_java=true
    else
        _jmajor=$(_check_java_version)
        [[ -z "${_jmajor}" || "${_jmajor}" -lt 17 ]] && _need_java=true && \
            echo -e "  ${YELLOW}⚠ Java ${_jmajor} 版本过低，需要 17+${RESET}"
    fi
    if [[ "${_need_java}" == "true" ]]; then
        echo -e "  ${YELLOW}→ 安装 openjdk@21...${RESET}"
        brew install openjdk@21
        export PATH="${BREW_PREFIX}/opt/openjdk@21/bin:${PATH}"
        sudo ln -sfn "${BREW_PREFIX}/opt/openjdk@21/libexec/openjdk.jdk" \
            /Library/Java/JavaVirtualMachines/openjdk-21.jdk 2>/dev/null || \
            warn "无法创建系统 JVM 链接（可选），已跳过"
        hash -r 2>/dev/null || true
    fi
    _jmajor=$(_check_java_version)
    [[ -z "${_jmajor}" ]] && die "Java 安装失败，请重新开终端再试"
    echo -e "  ${GREEN}✓ Java ${_jmajor}${RESET}"

    # Docker（仅本地模式需要；远程模式在 openbe start 时再检查）
    echo -e "  检查 Docker..."
    if ! command -v docker &>/dev/null; then
        echo -e "  ${YELLOW}→ 安装 OrbStack...${RESET}"
        brew install --cask orbstack
        warn "请打开 OrbStack.app 完成初始化后再执行 openbe start"
    fi
    command -v docker &>/dev/null && info "$(docker --version 2>&1 || true)"

    # Maven（仅本地模式需要）
    if [[ "${INSTALL_MODE}" == "local" ]]; then
        echo -e "  检查 Maven..."
        if ! command -v mvn &>/dev/null; then
            echo -e "  ${YELLOW}→ 安装 maven...${RESET}"
            brew install maven
            export PATH="${BREW_PREFIX}/bin:${PATH}"
            hash -r 2>/dev/null || true
        fi
        command -v mvn &>/dev/null || die "Maven 安装失败"
        info "$(mvn -version 2>&1 | head -1 || true)"
    fi

# ──────────────────────────────────────────────────────────
# Linux 依赖
# ──────────────────────────────────────────────────────────
else
    SUDO=""; [[ "$(id -u)" -ne 0 ]] && SUDO="sudo"

    echo -e "  检查 Java..."
    _need_java=false
    if ! command -v java &>/dev/null; then _need_java=true
    else
        _jmajor=$(_check_java_version)
        [[ -z "${_jmajor}" || "${_jmajor}" -lt 17 ]] && _need_java=true
    fi
    if [[ "${_need_java}" == "true" ]]; then
        echo -e "  ${YELLOW}→ 安装 OpenJDK 21...${RESET}"
        case "${PKG_MGR}" in
            apt)
                ${SUDO} apt-get update -qq
                if apt-cache show openjdk-21-jdk &>/dev/null 2>&1; then
                    ${SUDO} apt-get install -y openjdk-21-jdk
                else
                    ${SUDO} apt-get install -y openjdk-17-jdk
                fi ;;
            dnf) ${SUDO} dnf install -y java-21-openjdk-devel 2>/dev/null || \
                 ${SUDO} dnf install -y java-17-openjdk-devel ;;
            yum) ${SUDO} yum install -y java-21-openjdk-devel 2>/dev/null || \
                 ${SUDO} yum install -y java-17-openjdk-devel ;;
        esac
        hash -r 2>/dev/null || true
    fi
    _jmajor=$(_check_java_version)
    [[ -z "${_jmajor}" ]] && die "Java 安装失败"
    echo -e "  ${GREEN}✓ Java ${_jmajor}${RESET}"

    echo -e "  检查 Docker..."
    if ! command -v docker &>/dev/null; then
        echo -e "  ${YELLOW}→ 安装 Docker Engine...${RESET}"
        case "${PKG_MGR}" in
            apt)
                ${SUDO} apt-get install -y ca-certificates curl gnupg lsb-release
                ${SUDO} install -m 0755 -d /etc/apt/keyrings
                curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
                    ${SUDO} gpg --dearmor -o /etc/apt/keyrings/docker.gpg
                echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
                    ${SUDO} tee /etc/apt/sources.list.d/docker.list > /dev/null
                ${SUDO} apt-get update -qq
                ${SUDO} apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
                ${SUDO} usermod -aG docker "${USER}" 2>/dev/null || true ;;
            dnf|yum)
                ${SUDO} ${PKG_MGR} install -y yum-utils
                ${SUDO} yum-config-manager --add-repo \
                    https://download.docker.com/linux/centos/docker-ce.repo
                ${SUDO} ${PKG_MGR} install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
                ${SUDO} systemctl enable --now docker
                ${SUDO} usermod -aG docker "${USER}" 2>/dev/null || true ;;
        esac
        hash -r 2>/dev/null || true
    fi
    command -v docker &>/dev/null && info "$(docker --version 2>&1 || true)"

    if [[ "${INSTALL_MODE}" == "local" ]]; then
        echo -e "  检查 Maven..."
        if ! command -v mvn &>/dev/null; then
            case "${PKG_MGR}" in
                apt) ${SUDO} apt-get install -y maven ;;
                dnf) ${SUDO} dnf install -y maven ;;
                yum) ${SUDO} yum install -y maven ;;
            esac
            hash -r 2>/dev/null || true
        fi
        command -v mvn &>/dev/null || die "Maven 安装失败"
        info "$(mvn -version 2>&1 | head -1 || true)"
    fi
fi

ok "依赖就绪"

# ============================================================
# Step 2 — 获取 JAR（远程下载 或 本地编译）
# ============================================================
step "Step 2/4  获取 OpenBe 程序包"

mkdir -p "${OPENBE_HOME}/workspace"

if [[ "${INSTALL_MODE}" == "remote" ]]; then
    # ── 远程模式：直接下载预构建 JAR ──────────────────────
    echo -e "  ${CYAN}→ 从 ${RELEASE_BASE} 下载预构建 JAR...${RESET}"

    # 检测 curl 是否支持 --progress-bar（curl >= 7.67）
    _CURL_PROGRESS=""
    curl --progress-bar /dev/null -o /dev/null 2>/dev/null && _CURL_PROGRESS="--progress-bar"

    # 必选下载（失败则中止安装）
    _download() {
        local url="$1" dest="$2"
        echo -e "  ${DIM}↓ $(basename "${dest}")${RESET}"
        if command -v curl &>/dev/null; then
            curl -fSL ${_CURL_PROGRESS} "${url}" -o "${dest}"
        elif command -v wget &>/dev/null; then
            wget -q "${url}" -O "${dest}"
        else
            die "需要 curl 或 wget 来下载文件"
        fi
    }

    # 可选下载（失败只 warn，不中止）
    _download_optional() {
        local url="$1" dest="$2" label="$3"
        echo -e "  ${DIM}↓ $(basename "${dest}") (可选)${RESET}"
        if command -v curl &>/dev/null; then
            curl -fSL ${_CURL_PROGRESS} "${url}" -o "${dest}" 2>/dev/null || \
                warn "${label} 下载失败，跳过（后续孵化该蜂种时需手动补充）"
        elif command -v wget &>/dev/null; then
            wget -q "${url}" -O "${dest}" 2>/dev/null || \
                warn "${label} 下载失败，跳过"
        fi
    }

    _download "${RELEASE_BASE}/openbe-queen.jar"    "${OPENBE_HOME}/openbe-queen.jar"
    _download "${RELEASE_BASE}/openbe-cli.jar"      "${OPENBE_HOME}/openbe-cli.jar"
    _download "${RELEASE_BASE}/docker-compose.yml"  "${OPENBE_HOME}/workspace/docker-compose.yml"

    _download_optional "${RELEASE_BASE}/worker-bee.jar" "${OPENBE_HOME}/worker-bee.jar" "worker-bee.jar"
    _download_optional "${RELEASE_BASE}/nurse-bee.jar"  "${OPENBE_HOME}/nurse-bee.jar"  "nurse-bee.jar"

    ok "程序包下载完成"
else
    # ── 本地模式：Maven 源码编译 ───────────────────────────
    echo -e "  ${CYAN}→ 源码编译（首次需下载依赖，请耐心等待）...${RESET}"
    cd "${WORKSPACE_DIR}"
    if ! mvn clean package -DskipTests -q; then
        die "Maven 编译失败，请检查上方 [ERROR] 行"
    fi

    # 复制核心 JAR 到 OPENBE_HOME
    cp "${WORKSPACE_DIR}/packages/openbe-queen/target/openbe-queen.jar" "${OPENBE_HOME}/openbe-queen.jar"
    cp "${WORKSPACE_DIR}/packages/openbe-cli/target/openbe-cli.jar"     "${OPENBE_HOME}/openbe-cli.jar"

    # worker-bee / nurse-bee（可选，路径：packages/openbe-hive/{name}/target/）
    _WORKER_JAR="${WORKSPACE_DIR}/packages/openbe-hive/worker-bee/target/worker-bee.jar"
    _NURSE_JAR="${WORKSPACE_DIR}/packages/openbe-hive/nurse-bee/target/nurse-bee.jar"

    if [[ -f "${_WORKER_JAR}" ]]; then
        cp "${_WORKER_JAR}" "${OPENBE_HOME}/worker-bee.jar"
    else
        warn "worker-bee.jar 未找到（${_WORKER_JAR}），孵化工蜂时需手动补充"
    fi

    if [[ -f "${_NURSE_JAR}" ]]; then
        cp "${_NURSE_JAR}" "${OPENBE_HOME}/nurse-bee.jar"
    else
        warn "nurse-bee.jar 未找到（${_NURSE_JAR}），孵化护蜂时需手动补充"
    fi

    cp "${WORKSPACE_DIR}/docker-compose.yml" "${OPENBE_HOME}/workspace/docker-compose.yml"
    ok "源码编译完成"
fi

# 验证核心 JAR
[[ -f "${OPENBE_HOME}/openbe-queen.jar" ]] || die "openbe-queen.jar 未找到"
[[ -f "${OPENBE_HOME}/openbe-cli.jar"   ]] || die "openbe-cli.jar 未找到"
info "Queen  JAR：$(du -sh "${OPENBE_HOME}/openbe-queen.jar" | cut -f1)"
info "CLI    JAR：$(du -sh "${OPENBE_HOME}/openbe-cli.jar"   | cut -f1)"

# ============================================================
# Step 3 — 安装 openbe 命令
# ============================================================
step "Step 3/4  安装 openbe 命令"

mkdir -p "${OPENBE_BIN}"

# 确定 Java 可执行路径（写死进 wrapper，确保全局命令能找到 java）
JAVA_BIN="$(command -v java)"

WRAPPER="${OPENBE_BIN}/openbe"
info "生成 wrapper → ${WRAPPER}"
cat > "${WRAPPER}" << WRAPEOF
#!/usr/bin/env bash
# OpenBe CLI wrapper — generated $(date '+%Y-%m-%d %H:%M')
export OPENBE_WORKSPACE="\${OPENBE_WORKSPACE:-${OPENBE_HOME}/workspace}"
exec "${JAVA_BIN}" \\
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \\
  --sun-misc-unsafe-memory-access=allow \\
  -Dio.netty.tryReflectionSetAccessible=true \\
  -jar "${OPENBE_HOME}/openbe-cli.jar" "\$@"
WRAPEOF
chmod +x "${WRAPPER}"

[[ ! -x "${WRAPPER}" ]] && die "wrapper 创建失败：${WRAPPER}"
ok "CLI 安装到 ${WRAPPER}"

# ============================================================
# Step 4 — 配置 PATH
# ============================================================
step "Step 4/4  配置 PATH"

DETECTED_SHELL="$(basename "${SHELL:-bash}")"
case "${DETECTED_SHELL}" in
    zsh)  RC_FILE="${ZDOTDIR:-$HOME}/.zshrc" ;;
    bash) RC_FILE="${HOME}/.bash_profile" ;;
    *)    RC_FILE="${HOME}/.profile" ;;
esac

if [[ "${OS_TYPE}" == "macos" ]]; then
    BREW_PREFIX="$(brew --prefix 2>/dev/null || echo /opt/homebrew)"
    JDK_PATH="${BREW_PREFIX}/opt/openjdk@21/bin"
    if ! grep -qF "openjdk@21" "${RC_FILE}" 2>/dev/null; then
        printf '\n# OpenJDK 21 (via Homebrew)\nexport PATH="%s:${PATH}"\n' "${JDK_PATH}" >> "${RC_FILE}"
        info "已将 openjdk@21 写入 ${RC_FILE}"
    fi
fi

if echo "${PATH}" | tr ':' '\n' | grep -qx "${OPENBE_BIN}"; then
    echo -e "  ${GREEN}✓${RESET} ${OPENBE_BIN} 已在 PATH"
else
    PATH_LINE="export PATH=\"\${PATH}:${OPENBE_BIN}\""
    if ! grep -qF "${OPENBE_BIN}" "${RC_FILE}" 2>/dev/null; then
        printf '\n# OpenBe CLI\n%s\n' "${PATH_LINE}" >> "${RC_FILE}"
        info "已将 ${OPENBE_BIN} 写入 ${RC_FILE}"
    fi
fi

export PATH="${PATH}:${OPENBE_BIN}"
hash -r 2>/dev/null || true

if command -v openbe &>/dev/null; then
    echo -e "  ${GREEN}✓${RESET} openbe 命令已就绪：$(command -v openbe)"
else
    warn "当前终端找不到 openbe，关闭后重新打开即可"
    info "或立即执行：source ${RC_FILE}"
fi

ok "PATH 配置完成"

# ============================================================
# 完成
# ============================================================
echo ""
echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════╗"
echo -e "║  ✨ OpenBe 安装完成！                        ║"
echo -e "╚══════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "  ${DIM}命令路径 ：${WRAPPER}"
echo -e "  数据目录 ：${OPENBE_HOME}${RESET}"
echo ""
echo -e "  ${BOLD}下一步：${RESET}"
echo ""
echo -e "  ${CYAN}# 若新终端找不到命令，执行一次：${RESET}"
echo -e "  ${BOLD}source ${RC_FILE}${RESET}"
echo ""
echo -e "  ${CYAN}# 启动蜂巢 & 打开控制台：${RESET}"
echo -e "  ${BOLD}openbe start${RESET}     🐝 启动（Redis + Queen）"
echo -e "  ${BOLD}openbe onboard${RESET}   🌐 在浏览器打开控制台"
echo -e "  ${BOLD}openbe status${RESET}    📊 运行状态"
echo -e "  ${BOLD}openbe stop${RESET}      🛑 关闭蜂巢"
echo -e "  ${BOLD}openbe --help${RESET}    📖 帮助"
echo ""
echo -e "${DIM}  日志：${OPENBE_HOME}/queen.log${RESET}"
echo ""
