"""
webhook.py — OpenBe Wings FastAPI Webhook 服务入口
FastAPI webhook service — main entry point for OpenBe Wings.

提供以下路由 / Provided routes:
  GET  /health           — 服务健康检查 / service liveness probe
  POST /webhook/feishu   — 飞书事件回调 / Feishu event callback
  GET  /webhook/wework   — 企业微信服务器 URL 验证 / WeWork URL verification
  POST /webhook/wework   — 企业微信消息推送 / WeWork message push

启动方式 / How to start:
  python webhook.py
  # 或 / or
  uvicorn webhook:app --host 0.0.0.0 --port 8081 --reload

飞书回调配置说明 / Feishu callback setup notes:
  1. 进入飞书开放平台 → 应用 → 事件订阅
  2. 填写请求地址：https://<your-domain>/webhook/feishu
  3. 添加事件：im.message.receive_v1（接收消息）
  4. 将 Verification Token 填入 .env: FEISHU_VERIFICATION_TOKEN
  5. 若开启加密，将 Encrypt Key 填入 .env: FEISHU_ENCRYPT_KEY

企业微信回调配置说明 / WeWork callback setup notes:
  1. 进入企业微信管理后台 → 应用 → 接收消息
  2. 填写接收消息服务器 URL：https://<your-domain>/webhook/wework
  3. 配置 Token / EncodingAESKey，对应 .env: WEWORK_TOKEN / WEWORK_ENCODING_AES_KEY
  4. 点击「保存」时企业微信会发送 GET 验证请求，服务需已启动
"""

import asyncio
import hashlib
import json
import logging
import logging.config
import os
import xml.etree.ElementTree as ET
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, PlainTextResponse

from config import Config
from handler import OpenBeHandler
from adapters.feishu import FeishuAdapter
from adapters.wework import WeWorkAdapter
from models import OpenBeMessage, PlatformType, MessageType

# ──────────────────────────────────────────────────────────────────────────────
# 日志配置（带时间戳）/ Logging configuration with timestamps
# ──────────────────────────────────────────────────────────────────────────────

logging.config.dictConfig(
    {
        "version": 1,
        "disable_existing_loggers": False,
        "formatters": {
            "standard": {
                "format": "%(asctime)s [%(levelname)-8s] %(name)s: %(message)s",
                "datefmt": "%Y-%m-%d %H:%M:%S",
            }
        },
        "handlers": {
            "console": {
                "class": "logging.StreamHandler",
                "stream": "ext://sys.stdout",
                "formatter": "standard",
            }
        },
        "root": {"handlers": ["console"], "level": "INFO"},
    }
)

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────────────────────
# 全局初始化 / Global initialisation
# ──────────────────────────────────────────────────────────────────────────────

# 加载配置单例 / Load config singleton (reads .env or env vars)
config = Config.from_env()

# openbe-queen 消息处理器（共享单例）/ Shared openbe-queen message handler
_handler = OpenBeHandler(
    queen_url=config.openbe_queen_url,
    hive_id=config.openbe_hive_id,
)

# 平台适配器（懒加载）/ Platform adapters (lazy-init on first request)
_feishu_adapter: FeishuAdapter | None = None
_wework_adapter: WeWorkAdapter | None = None


def _get_feishu_adapter() -> FeishuAdapter:
    """懒加载并校验飞书适配器 / Lazy-load and validate Feishu adapter."""
    global _feishu_adapter
    if _feishu_adapter is None:
        config.validate_feishu()
        _feishu_adapter = FeishuAdapter(config)
        logger.info("飞书适配器初始化完成 / Feishu adapter initialised.")
    return _feishu_adapter


def _get_wework_adapter() -> WeWorkAdapter:
    """懒加载并校验企业微信适配器 / Lazy-load and validate WeWork adapter."""
    global _wework_adapter
    if _wework_adapter is None:
        config.validate_wework()
        _wework_adapter = WeWorkAdapter(config)
        logger.info("企业微信适配器初始化完成 / WeWork adapter initialised.")
    return _wework_adapter


# ──────────────────────────────────────────────────────────────────────────────
# 辅助函数 / Helpers
# ──────────────────────────────────────────────────────────────────────────────

def _sync_handle(msg: OpenBeMessage) -> str:
    """
    在独立事件循环中执行 handler.handle()（供线程池使用）。
    Execute handler.handle() in a dedicated event loop (for thread pool use).

    handler.handle() 是 async 方法，但 requests 内部为同步 IO；
    将其放入线程池执行以避免阻塞主事件循环。
    handler.handle() is async but uses sync requests internally;
    run it in a thread pool to keep the main event loop unblocked.
    """
    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(_handler.handle(msg))
    finally:
        loop.close()


def _wework_sign(token: str, timestamp: str, nonce: str, msg_encrypt: str) -> str:
    """
    计算企业微信消息签名（SHA1）。
    Compute WeWork message signature (SHA1).
    """
    pieces = sorted([token, timestamp, nonce, msg_encrypt])
    raw = "".join(pieces).encode("utf-8")
    return hashlib.sha1(raw).hexdigest()


# ──────────────────────────────────────────────────────────────────────────────
# FastAPI 生命周期 / FastAPI lifespan
# ──────────────────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(application: FastAPI):
    """服务启动 / 关闭生命周期 / Service startup/shutdown lifecycle."""
    logger.info(
        "OpenBe Wings 启动 / OpenBe Wings starting. "
        "queen=%s hive=%s port=%d",
        config.openbe_queen_url,
        config.openbe_hive_id,
        config.wings_port,
    )
    yield
    logger.info("OpenBe Wings 关闭 / OpenBe Wings shutting down.")


# ──────────────────────────────────────────────────────────────────────────────
# FastAPI 应用实例 / FastAPI application instance
# ──────────────────────────────────────────────────────────────────────────────

app = FastAPI(
    title="OpenBe Wings",
    description="OpenBe Wings — 飞书 & 企业微信消息适配器 / Feishu & WeWork adapter",
    version="1.0.0",
    lifespan=lifespan,
)


# ──────────────────────────────────────────────────────────────────────────────
# 健康检查 / Health check
# ──────────────────────────────────────────────────────────────────────────────

@app.get("/health", tags=["system"])
async def health():
    """Wings 服务健康检查 / Wings service liveness probe."""
    return {"status": "ok", "service": "openbe-wings"}


# ──────────────────────────────────────────────────────────────────────────────
# 飞书 Webhook / Feishu Webhook
# ──────────────────────────────────────────────────────────────────────────────

@app.post("/webhook/feishu", tags=["feishu"])
async def feishu_webhook(request: Request):
    """
    飞书事件回调入口。
    Feishu event callback endpoint.

    处理流程 / Processing flow:
      1. 读取原始请求体并解析 JSON
      2. 处理 url_verification 挑战（首次配置时必须优先响应）
      3. 验证 X-Lark-Signature 请求签名（可选，配置了 token 时启用）
      4. 将 payload 转换为 OpenBeMessage
      5. 转发给 openbe-queen 获取 AI 回复（线程池执行同步 HTTP）
      6. 通过飞书 API 将回复发回给用户
      7. 返回 {"code": 0, "msg": "success"}
    """
    raw_body = await request.body()

    # ── 步骤 1：解析 JSON ─────────────────────────────────────────────────
    try:
        payload: dict = json.loads(raw_body)
    except json.JSONDecodeError as exc:
        logger.error("飞书回调 JSON 解析失败 / Feishu callback JSON parse failed: %s", exc)
        raise HTTPException(status_code=400, detail="Invalid JSON body") from exc

    logger.info(
        "飞书回调到达 / Feishu callback received. type=%s",
        payload.get("type") or payload.get("header", {}).get("event_type", "unknown"),
    )

    try:
        adapter = _get_feishu_adapter()
    except ValueError as exc:
        logger.error("飞书配置缺失 / Feishu config missing: %s", exc)
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    # ── 步骤 2：URL 验证挑战 ──────────────────────────────────────────────
    # 飞书首次配置回调地址时发送 challenge，必须立即原样返回
    # Feishu sends a challenge on first callback URL config — must echo it back.
    challenge_resp = adapter.handle_challenge(payload)
    if challenge_resp is not None:
        logger.info(
            "返回飞书 URL 验证 challenge / Returning Feishu URL verification challenge."
        )
        return JSONResponse(content=challenge_resp)

    # ── 步骤 3：签名验证 ──────────────────────────────────────────────────
    # 从请求头读取飞书签名相关字段 / read Feishu signature headers
    timestamp = request.headers.get("X-Lark-Request-Timestamp", "")
    nonce = request.headers.get("X-Lark-Request-Nonce", "")
    signature = request.headers.get("X-Lark-Signature", "")

    # 仅在配置了 verification_token 且请求携带签名时才做验证
    # Only verify when verification_token is configured AND signature is present.
    if config.feishu_verification_token and signature:
        if not adapter.verify_signature(timestamp, nonce, signature, raw_body):
            logger.warning(
                "飞书签名验证失败，拒绝请求 / Feishu signature failed, rejecting."
            )
            raise HTTPException(status_code=403, detail="Invalid signature")

    # ── 步骤 4：消息转换 ──────────────────────────────────────────────────
    try:
        openbe_msg = adapter.convert_to_openbe_msg(payload)
    except Exception as exc:  # pylint: disable=broad-except
        logger.error("飞书消息解析失败 / Feishu message parse failed: %s", exc)
        # 返回 200 避免飞书平台持续重试 / return 200 to prevent Feishu retry loop
        return JSONResponse(content={"code": 0, "msg": "parse_error"})

    if openbe_msg is None:
        # 无法解析的事件（非消息类、机器人自身消息等），静默返回 200
        # Unparsable event (non-message, bot's own message, etc.) — silent 200.
        logger.info("飞书事件无需处理，已忽略 / Feishu event skipped (no-op).")
        return JSONResponse(content={"code": 0, "msg": "ignored"})

    logger.info("收到飞书消息 / Received Feishu message: %s", openbe_msg)

    # ── 步骤 5：获取 AI 回复（线程池执行，避免阻塞主循环）────────────────
    loop = asyncio.get_event_loop()
    try:
        reply_text: str = await loop.run_in_executor(
            None, lambda: _sync_handle(openbe_msg)
        )
    except Exception as exc:  # pylint: disable=broad-except
        logger.exception(
            "处理飞书消息异常 / Exception while handling Feishu message: %s", exc
        )
        reply_text = "系统繁忙，请稍后重试。/ System busy, please try again later."

    # ── 步骤 6：回复用户 ──────────────────────────────────────────────────
    try:
        reply_msg = OpenBeMessage(
            user_id=openbe_msg.user_id,
            platform=PlatformType.FEISHU,
            content=reply_text,
            msg_type=MessageType.TEXT,
            session_id=openbe_msg.session_id,
        )
        adapter.convert_from_openbe_msg(reply_msg)
    except Exception as exc:  # pylint: disable=broad-except
        logger.exception(
            "发送飞书回复失败 / Failed to send Feishu reply: %s", exc
        )
        # 发送失败不影响返回 200，防止飞书平台重复推送
        # Don't propagate — prevents Feishu retry storms.

    return JSONResponse(content={"code": 0, "msg": "success"})


# ──────────────────────────────────────────────────────────────────────────────
# 企业微信 Webhook / WeWork Webhook
# ──────────────────────────────────────────────────────────────────────────────

@app.get("/webhook/wework", tags=["wework"])
async def wework_verify(
    msg_signature: str = "",
    timestamp: str = "",
    nonce: str = "",
    echostr: str = "",
):
    """
    企业微信服务器 URL 验证（GET 请求）。
    WeWork server URL verification (GET request).

    企业微信在保存回调配置时发送 GET 请求；
    验证签名后原样返回 echostr 即通过验证。
    WeWork sends a GET request when saving callback config;
    return echostr as plain text after signature check.
    """
    logger.info(
        "企业微信 URL 验证请求 / WeWork URL verification request. "
        "timestamp=%s nonce=%s",
        timestamp,
        nonce,
    )

    try:
        adapter = _get_wework_adapter()
    except ValueError as exc:
        logger.error("企业微信配置缺失 / WeWork config missing: %s", exc)
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    result = adapter.verify_signature(msg_signature, timestamp, nonce, echostr)
    if not result:
        logger.warning("企业微信 URL 验证失败 / WeWork URL verification failed.")
        raise HTTPException(status_code=403, detail="Signature verification failed")

    logger.info("企业微信 URL 验证通过 / WeWork URL verification passed.")
    # 企业微信要求返回纯文本 echostr / WeWork requires plain-text echostr
    return PlainTextResponse(content=result)


@app.post("/webhook/wework", tags=["wework"])
async def wework_webhook(
    request: Request,
    msg_signature: str = "",
    timestamp: str = "",
    nonce: str = "",
):
    """
    企业微信消息推送回调（POST 请求）。
    WeWork message push callback (POST request).

    处理流程 / Processing flow:
      1. 读取原始 XML 请求体
      2. 解析 XML（支持加密消息体）
      3. 转换为 OpenBeMessage
      4. 转发给 openbe-queen 获取 AI 回复
      5. 通过企业微信 API 回复用户
      6. 返回空字符串（企业微信规范要求）

    注意：企业微信 POST 回调期望响应为空字符串，而非 JSON。
    Note: WeWork POST callback expects an empty string response, not JSON.
    """
    raw_body = await request.body()
    logger.info(
        "企业微信消息回调 / WeWork message callback. body_size=%d bytes",
        len(raw_body),
    )

    try:
        adapter = _get_wework_adapter()
    except ValueError as exc:
        logger.error("企业微信配置缺失 / WeWork config missing: %s", exc)
        # 必须返回 200，否则企业微信会触发重试 / must return 200 to avoid retries
        return PlainTextResponse(content="")

    # ── 步骤 1：解析 XML（支持加密消息体）────────────────────────────────
    try:
        root = ET.fromstring(raw_body.decode("utf-8"))
    except ET.ParseError as exc:
        logger.error("企业微信 XML 解析失败 / WeWork XML parse failed: %s", exc)
        return PlainTextResponse(content="")

    # 若存在 <Encrypt> 节点且配置了 AES 密钥，先验证再解密
    # If <Encrypt> node exists and AES key is configured, verify then decrypt.
    encrypt_elem = root.find("Encrypt")
    if encrypt_elem is not None and config.wework_encoding_aes_key:
        encrypt_str = encrypt_elem.text or ""
        # 验证签名 / verify signature
        computed = _wework_sign(config.wework_token, timestamp, nonce, encrypt_str)
        if computed != msg_signature:
            logger.warning(
                "企业微信消息签名验证失败 / WeWork message signature verification failed."
            )
            # 签名失败但仍返回 200，防止企业微信重试轰炸
            # Return 200 even on failure to prevent WeWork retry flood.
            return PlainTextResponse(content="")
        # 解密（此处仅做签名验证；完整 AES 解密需引入第三方库，按需扩展）
        # Decryption placeholder — full AES decrypt requires a 3rd-party lib.
        logger.info(
            "企业微信消息已加密，签名验证通过 / "
            "WeWork message is encrypted; signature verified. "
            "Full AES decryption can be added here if needed."
        )

    # ── 步骤 2：提取字段并转换消息 ────────────────────────────────────────
    xml_dict = {child.tag: (child.text or "") for child in root}
    logger.debug("企业微信原始消息 dict / WeWork raw message dict: %s", xml_dict)

    openbe_msg = adapter.convert_to_openbe_msg(xml_dict)
    if openbe_msg is None:
        logger.info("企业微信消息无需处理，已忽略 / WeWork message skipped (no-op).")
        return PlainTextResponse(content="")

    logger.info("收到企业微信消息 / Received WeWork message: %s", openbe_msg)

    # ── 步骤 3：获取 AI 回复（线程池执行）────────────────────────────────
    loop = asyncio.get_event_loop()
    try:
        reply_text: str = await loop.run_in_executor(
            None, lambda: _sync_handle(openbe_msg)
        )
    except Exception as exc:  # pylint: disable=broad-except
        logger.exception(
            "处理企业微信消息异常 / Exception while handling WeWork message: %s", exc
        )
        reply_text = "系统繁忙，请稍后重试。"

    # ── 步骤 4：回复用户 ──────────────────────────────────────────────────
    try:
        reply_msg = OpenBeMessage(
            user_id=openbe_msg.user_id,
            platform=PlatformType.WEWORK,
            content=reply_text,
            msg_type=MessageType.TEXT,
            session_id=openbe_msg.session_id,
        )
        adapter.convert_from_openbe_msg(reply_msg)
    except Exception as exc:  # pylint: disable=broad-except
        logger.exception(
            "发送企业微信回复失败 / Failed to send WeWork reply: %s", exc
        )

    # 企业微信要求 POST 回调返回空字符串 / WeWork requires empty string response
    return PlainTextResponse(content="")


# ──────────────────────────────────────────────────────────────────────────────
# 全局异常处理 / Global exception handler
# ──────────────────────────────────────────────────────────────────────────────

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """捕获所有未处理异常并返回 500 / Catch all unhandled exceptions, return 500."""
    logger.exception(
        "未处理的异常 / Unhandled exception on %s: %s", request.url, exc
    )
    return JSONResponse(
        status_code=500,
        content={"code": 500, "msg": "Internal server error"},
    )


# ──────────────────────────────────────────────────────────────────────────────
# 服务启动入口 / Service startup entry point
# ──────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    logger.info(
        "启动 OpenBe Wings 服务 / Starting OpenBe Wings service. "
        "host=0.0.0.0 port=%d queen=%s hive=%s",
        config.wings_port,
        config.openbe_queen_url,
        config.openbe_hive_id,
    )
    uvicorn.run(
        "webhook:app",
        host="0.0.0.0",
        port=config.wings_port,
        reload=False,   # 生产环境关闭热重载 / disable hot-reload in production
        log_level="info",
    )
