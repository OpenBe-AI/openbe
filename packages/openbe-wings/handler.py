"""
handler.py — OpenBe Wings 消息路由处理器
OpenBe Wings message routing handler.

OpenBeHandler 接收统一格式的 OpenBeMessage，将其转发给 openbe-queen 的
Chat API，并将 AI 的回复文本返回给调用方。

OpenBeHandler receives a unified OpenBeMessage, forwards it to the
openbe-queen Chat API, and returns the AI reply text to the caller.

openbe-queen Chat API 契约 / openbe-queen Chat API contract:
  POST {queen_url}/api/hives/{hive_id}/chat
  Request body : {"question": "<user message text>"}
  Response body: {"answer": "<AI reply text>", ...}
"""

import logging
from typing import Optional

import requests

from models import OpenBeMessage

logger = logging.getLogger(__name__)

# HTTP 请求超时（秒）/ HTTP request timeout in seconds
_DEFAULT_TIMEOUT = 30


class OpenBeHandler:
    """
    将 OpenBeMessage 路由至 openbe-queen 并返回 AI 回复。
    Routes an OpenBeMessage to openbe-queen and returns the AI reply.

    设计为无状态（stateless）——同一实例可安全地并发处理多条消息。
    Designed to be stateless — a single instance safely handles
    concurrent messages.
    """

    def __init__(
        self,
        queen_url: str,
        hive_id: str = "default",
        timeout: int = _DEFAULT_TIMEOUT,
    ) -> None:
        """
        Args:
            queen_url: openbe-queen 服务地址，例如 "http://localhost:8080"
                       openbe-queen service base URL, e.g. "http://localhost:8080"
            hive_id  : 目标 Hive 的 ID（对应 queen 中的对话/应用实例）
                       Target Hive ID (corresponds to a conversation/app instance in queen)
            timeout  : HTTP 请求超时秒数 / HTTP request timeout in seconds
        """
        # 去掉末尾斜杠，保持 URL 拼接一致性 / strip trailing slash for clean URL join
        self._queen_url = queen_url.rstrip("/")
        self._hive_id = hive_id
        self._timeout = timeout

        # 构建 Chat API endpoint / build Chat API endpoint
        self._chat_endpoint = (
            f"{self._queen_url}/api/hives/{self._hive_id}/chat"
        )

        logger.info(
            "OpenBeHandler 初始化 / OpenBeHandler initialised. "
            "endpoint=%s",
            self._chat_endpoint,
        )

    # ──────────────────────────────────────────────────────────────────────
    # 核心路由 / Core routing
    # ──────────────────────────────────────────────────────────────────────

    async def handle(self, msg: OpenBeMessage) -> str:
        """
        将消息发送给 openbe-queen，返回 AI 回复文本。
        Send the message to openbe-queen and return the AI reply text.

        调用 POST {queen_url}/api/hives/{hive_id}/chat，
        请求体为 {"question": msg.content}，
        解析响应中的 "answer" 字段并返回。

        Calls POST {queen_url}/api/hives/{hive_id}/chat,
        with body {"question": msg.content},
        and returns the "answer" field from the response.

        Args:
            msg: 待处理的统一消息 / the unified message to handle

        Returns:
            AI 回复文本；若调用失败则返回友好的错误提示
            AI reply text; a friendly error message on failure.
        """
        logger.info(
            "处理消息 / Handling message: user=%s platform=%s session=%s",
            msg.user_id,
            msg.platform.value,
            msg.session_id,
        )

        # 目前仅文本内容可以直接发给 queen；图片类型给出提示
        # Only text content is forwarded directly to queen; image gets a notice.
        if not msg.content.strip():
            logger.warning(
                "消息内容为空，跳过转发 / Message content is empty, skipping forward."
            )
            return "收到空消息，请发送文字内容。/ Received empty message, please send text."

        payload = {"question": msg.content}

        try:
            # 使用同步 requests —— FastAPI 的 async 端点可通过
            # asyncio.get_event_loop().run_in_executor 调用同步代码，
            # 但本项目规模下同步调用足够；如需真正异步请替换为 httpx。
            # Using synchronous requests. For truly async calls, replace with httpx.
            response = requests.post(
                self._chat_endpoint,
                json=payload,
                timeout=self._timeout,
                headers={"Content-Type": "application/json"},
            )
            response.raise_for_status()

        except requests.Timeout:
            logger.error(
                "openbe-queen 请求超时 / openbe-queen request timed out. "
                "endpoint=%s timeout=%ds",
                self._chat_endpoint,
                self._timeout,
            )
            return "AI 处理超时，请稍后重试。/ AI processing timed out, please try again."

        except requests.ConnectionError as exc:
            logger.error(
                "无法连接 openbe-queen / Cannot connect to openbe-queen: %s", exc
            )
            return "无法连接 AI 服务，请联系管理员。/ Cannot reach AI service, contact admin."

        except requests.HTTPError as exc:
            logger.error(
                "openbe-queen 返回 HTTP 错误 / openbe-queen returned HTTP error: %s",
                exc,
            )
            return f"AI 服务返回错误（{response.status_code}），请稍后重试。"

        # 解析响应 / parse response
        try:
            data: dict = response.json()
        except ValueError:
            logger.error(
                "openbe-queen 响应不是有效 JSON / "
                "openbe-queen response is not valid JSON: %s",
                response.text[:200],
            )
            return "AI 服务响应异常，请稍后重试。/ Unexpected AI response, please retry."

        # 优先取 "answer"，其次取 "response"/"text"，最后原样返回 JSON 字符串
        # Prefer "answer", then "response"/"text", finally raw JSON string.
        reply: str = (
            data.get("answer")
            or data.get("response")
            or data.get("text")
            or str(data)
        )

        logger.info(
            "openbe-queen 回复 / openbe-queen replied: %s...",
            reply[:80],
        )
        return reply

    # ──────────────────────────────────────────────────────────────────────
    # 工具方法 / Utility
    # ──────────────────────────────────────────────────────────────────────

    def health_check(self) -> bool:
        """
        简单探活：向 queen 发送 GET /health 请求。
        Simple liveness probe: send GET /health to queen.

        Returns:
            True 若 queen 正常响应 / True if queen responds normally.
        """
        url = f"{self._queen_url}/health"
        try:
            resp = requests.get(url, timeout=5)
            return resp.status_code == 200
        except requests.RequestException:
            return False
