"""
adapters/feishu.py — 飞书 (Feishu / Lark) 平台适配器
Feishu platform adapter for OpenBe Wings.

包含两个核心类：
  - FeishuAPI   : 封装飞书开放平台 REST API（含 access_token 内存缓存）
  - FeishuAdapter: 负责消息格式在飞书原始 payload 与 OpenBeMessage 之间的互转

飞书回调配置说明 / Feishu callback setup notes:
  1. 在飞书开放平台创建企业自建应用
  2. 进入「事件订阅」→ 开启订阅，填写回调地址：
         https://<your-domain>/webhook/feishu
  3. 订阅事件：im.message.receive_v1（接收消息）
  4. 若启用消息加密，在「安全设置」中设置 Encrypt Key，
     并在 .env 中填写 FEISHU_ENCRYPT_KEY
  5. 将 Verification Token 填入 .env 的 FEISHU_VERIFICATION_TOKEN
"""

import hashlib
import hmac
import json
import logging
import time
from typing import Optional

import requests

from models import MessageType, OpenBeMessage, PlatformType
from config import Config

logger = logging.getLogger(__name__)

# 飞书开放平台 API 基础地址 / Feishu Open Platform API base URL
FEISHU_API_BASE = "https://open.feishu.cn/open-apis"


class FeishuAPI:
    """
    飞书开放平台 API 客户端
    Feishu Open Platform API client.

    提供 access_token 的自动获取与缓存，以及消息发送功能。
    Handles automatic access_token fetching with in-memory caching
    and message sending utilities.
    """

    def __init__(self, app_id: str, app_secret: str) -> None:
        self._app_id = app_id
        self._app_secret = app_secret

        # access_token 内存缓存 / in-memory token cache
        self._token: str = ""
        self._token_expires_at: float = 0.0  # Unix timestamp

    # ──────────────────────────────────────────────────────────────────────
    # Token 管理 / Token management
    # ──────────────────────────────────────────────────────────────────────

    def get_access_token(self) -> str:
        """
        获取有效的 tenant_access_token，内置 300 秒缓冲区。
        Returns a valid tenant_access_token with a 300-second buffer
        before actual expiry to avoid last-second failures.

        Returns:
            有效的 access_token 字符串 / valid access_token string

        Raises:
            RuntimeError: token 获取失败时 / on API failure
        """
        # 若 token 仍在有效期内（含 300s 缓冲），直接复用
        if self._token and time.time() < self._token_expires_at:
            return self._token

        logger.info("正在刷新飞书 access_token / Refreshing Feishu access_token...")
        url = f"{FEISHU_API_BASE}/auth/v3/tenant_access_token/internal"
        payload = {
            "app_id": self._app_id,
            "app_secret": self._app_secret,
        }

        try:
            resp = requests.post(url, json=payload, timeout=10)
            resp.raise_for_status()
            data = resp.json()
        except requests.RequestException as exc:
            logger.error("飞书 token 请求失败 / Feishu token request failed: %s", exc)
            raise RuntimeError(f"Failed to fetch Feishu access_token: {exc}") from exc

        if data.get("code") != 0:
            msg = data.get("msg", "unknown error")
            logger.error(
                "飞书 token 接口返回错误 / Feishu token API error: code=%s msg=%s",
                data.get("code"),
                msg,
            )
            raise RuntimeError(f"Feishu token API error: {msg}")

        expires_in: int = data.get("expire", 7200)
        self._token = data["tenant_access_token"]
        # 提前 300 秒视为过期，避免边界情况 / treat as expired 300s early
        self._token_expires_at = time.time() + expires_in - 300

        logger.info(
            "飞书 access_token 刷新成功，有效期 %d 秒 / "
            "Feishu access_token refreshed, expires_in=%ds",
            expires_in,
        )
        return self._token

    # ──────────────────────────────────────────────────────────────────────
    # 消息发送 / Message sending
    # ──────────────────────────────────────────────────────────────────────

    def _auth_headers(self) -> dict:
        """构建带鉴权信息的请求头 / Build request headers with auth token."""
        return {
            "Authorization": f"Bearer {self.get_access_token()}",
            "Content-Type": "application/json; charset=utf-8",
        }

    def send_text_message(self, open_id: str, text: str) -> dict:
        """
        向指定用户发送纯文本消息。
        Send a plain-text message to the specified user.

        Args:
            open_id: 接收者的飞书 open_id / recipient's Feishu open_id
            text   : 消息正文 / message body

        Returns:
            飞书 API 响应 JSON / Feishu API response dict

        Raises:
            RuntimeError: 发送失败时 / on send failure
        """
        url = f"{FEISHU_API_BASE}/im/v1/messages"
        params = {"receive_id_type": "open_id"}
        payload = {
            "receive_id": open_id,
            "msg_type": "text",
            "content": json.dumps({"text": text}, ensure_ascii=False),
        }

        try:
            resp = requests.post(
                url,
                params=params,
                json=payload,
                headers=self._auth_headers(),
                timeout=10,
            )
            resp.raise_for_status()
            data = resp.json()
        except requests.RequestException as exc:
            logger.error(
                "飞书发送文本消息失败 / Failed to send Feishu text message: %s", exc
            )
            raise RuntimeError(f"Failed to send Feishu text message: {exc}") from exc

        if data.get("code") != 0:
            logger.error(
                "飞书发送消息接口错误 / Feishu send message error: %s", data.get("msg")
            )
            raise RuntimeError(f"Feishu send message error: {data.get('msg')}")

        logger.debug("飞书文本消息发送成功 / Feishu text message sent to %s", open_id)
        return data

    def send_card_message(self, open_id: str, card_content: dict) -> dict:
        """
        向指定用户发送飞书交互卡片消息。
        Send an interactive card message to the specified user.

        Args:
            open_id     : 接收者的飞书 open_id / recipient's Feishu open_id
            card_content: 卡片 JSON 内容（符合飞书卡片规范）
                          Card JSON conforming to Feishu card spec

        Returns:
            飞书 API 响应 JSON / Feishu API response dict
        """
        url = f"{FEISHU_API_BASE}/im/v1/messages"
        params = {"receive_id_type": "open_id"}
        payload = {
            "receive_id": open_id,
            "msg_type": "interactive",
            "content": json.dumps(card_content, ensure_ascii=False),
        }

        try:
            resp = requests.post(
                url,
                params=params,
                json=payload,
                headers=self._auth_headers(),
                timeout=10,
            )
            resp.raise_for_status()
            data = resp.json()
        except requests.RequestException as exc:
            logger.error(
                "飞书发送卡片消息失败 / Failed to send Feishu card message: %s", exc
            )
            raise RuntimeError(f"Failed to send Feishu card message: {exc}") from exc

        if data.get("code") != 0:
            logger.error(
                "飞书卡片消息接口错误 / Feishu card message error: %s",
                data.get("msg"),
            )
            raise RuntimeError(f"Feishu card message error: {data.get('msg')}")

        logger.debug("飞书卡片消息发送成功 / Feishu card message sent to %s", open_id)
        return data


class FeishuAdapter:
    """
    飞书消息格式适配器
    Feishu message format adapter.

    负责在飞书原始 webhook payload 与 OpenBeMessage 之间进行双向转换。
    Handles bi-directional conversion between Feishu raw webhook payloads
    and the universal OpenBeMessage format.
    """

    def __init__(self, config: Optional[Config] = None) -> None:
        cfg = config or Config.from_env()
        self._verification_token = cfg.feishu_verification_token
        self._encrypt_key = cfg.feishu_encrypt_key
        # 共享一个 API 客户端实例 / share a single API client instance
        self._api = FeishuAPI(cfg.feishu_app_id, cfg.feishu_app_secret)

    # ──────────────────────────────────────────────────────────────────────
    # URL 验证挑战 / URL verification challenge
    # ──────────────────────────────────────────────────────────────────────

    def handle_challenge(self, payload: dict) -> Optional[dict]:
        """
        处理飞书 URL 验证事件（url_verification）。
        Handle Feishu URL verification challenge event.

        飞书在配置回调地址时会发送 challenge 请求，服务器必须原样返回
        challenge 字段才能通过验证。
        Feishu sends a challenge request when the callback URL is first
        configured. The server must echo back the challenge value.

        Args:
            payload: 飞书推送的原始 JSON / raw Feishu push payload

        Returns:
            {"challenge": <value>} 若是 challenge 事件，否则返回 None
            {"challenge": <value>} if it's a challenge event, else None.
        """
        if payload.get("type") == "url_verification":
            challenge = payload.get("challenge", "")
            logger.info(
                "收到飞书 URL 验证挑战 / Received Feishu URL verification challenge."
            )
            return {"challenge": challenge}
        return None

    # ──────────────────────────────────────────────────────────────────────
    # 签名验证 / Signature verification
    # ──────────────────────────────────────────────────────────────────────

    def verify_signature(
        self,
        timestamp: str,
        nonce: str,
        signature: str,
        body: bytes,
    ) -> bool:
        """
        验证飞书回调的请求签名（X-Lark-Signature）。
        Verify the Feishu callback request signature (X-Lark-Signature).

        签名算法：sha256(timestamp + nonce + verification_token + body_str)
        Signature algorithm: sha256(timestamp + nonce + verification_token + body_str)

        Args:
            timestamp : 请求头 X-Lark-Request-Timestamp
            nonce     : 请求头 X-Lark-Request-Nonce
            signature : 请求头 X-Lark-Signature
            body      : 原始请求体字节 / raw request body bytes

        Returns:
            True 若签名合法 / True if signature is valid
        """
        content = timestamp + nonce + self._verification_token + body.decode("utf-8")
        expected = hashlib.sha256(content.encode("utf-8")).hexdigest()
        valid = hmac.compare_digest(expected, signature)
        if not valid:
            logger.warning(
                "飞书签名验证失败 / Feishu signature verification failed. "
                "expected=%s, got=%s",
                expected,
                signature,
            )
        return valid

    # ──────────────────────────────────────────────────────────────────────
    # 消息转换 / Message conversion
    # ──────────────────────────────────────────────────────────────────────

    def convert_to_openbe_msg(self, feishu_payload: dict) -> Optional[OpenBeMessage]:
        """
        将飞书 webhook payload 转换为 OpenBeMessage。
        Convert a Feishu webhook payload into an OpenBeMessage.

        支持的飞书消息类型 / Supported Feishu message types:
          - text  : 文本消息
          - image : 图片消息（content 为 image_key）

        Args:
            feishu_payload: 飞书推送的原始 JSON / raw Feishu push payload

        Returns:
            转换后的 OpenBeMessage，若无法解析则返回 None
            Converted OpenBeMessage, or None if parsing fails.
        """
        try:
            # 飞书事件回调 v2.0 结构：event.header + event.event
            header = feishu_payload.get("header", {})
            event = feishu_payload.get("event", {})

            event_type = header.get("event_type", "")
            # 只处理消息接收事件 / only handle message receive events
            if event_type != "im.message.receive_v1":
                logger.debug(
                    "忽略非消息事件 / Skipping non-message event: %s", event_type
                )
                return None

            message = event.get("message", {})
            sender = event.get("sender", {})

            # 发送者 open_id / sender's open_id
            open_id: str = sender.get("sender_id", {}).get("open_id", "")
            if not open_id:
                logger.warning(
                    "飞书消息缺少 sender open_id / Feishu message missing sender open_id"
                )
                return None

            # 消息 ID 作为 session_id / message_id as session_id
            session_id: str = message.get("message_id", "")
            raw_msg_type: str = message.get("message_type", "text")

            # 解析消息内容（content 字段为 JSON 字符串）
            # Parse message content (content field is a JSON string)
            content_str: str = message.get("content", "{}")
            try:
                content_dict: dict = json.loads(content_str)
            except json.JSONDecodeError:
                content_dict = {}

            # 根据消息类型提取正文 / extract body by message type
            if raw_msg_type == "text":
                content = content_dict.get("text", "").strip()
                # 飞书 @机器人 时文本可能含 <at> 标签，简单清理一下
                # Strip @mention tags that Feishu may insert
                import re
                content = re.sub(r"<at[^>]*>[^<]*</at>", "", content).strip()
                msg_type = MessageType.TEXT

            elif raw_msg_type == "image":
                # 图片消息：提取 image_key 作为内容标识
                content = content_dict.get("image_key", "")
                msg_type = MessageType.IMAGE

            else:
                # 其他类型暂时以 text 类型兜底，content 为原始 JSON
                logger.info(
                    "飞书消息类型 %r 暂不支持，将原始内容透传 / "
                    "Unsupported Feishu message type %r, passing raw content.",
                    raw_msg_type,
                )
                content = content_str
                msg_type = MessageType.TEXT

            return OpenBeMessage(
                user_id=open_id,
                platform=PlatformType.FEISHU,
                content=content,
                msg_type=msg_type,
                session_id=session_id,
                raw_data=feishu_payload,
            )

        except Exception as exc:  # pylint: disable=broad-except
            logger.exception(
                "飞书消息转换失败 / Failed to convert Feishu payload: %s", exc
            )
            return None

    def convert_from_openbe_msg(self, msg: OpenBeMessage) -> None:
        """
        将 OpenBeMessage 通过飞书 API 发送出去。
        Send an OpenBeMessage back to the user via the Feishu API.

        目前支持发送文本消息和卡片消息。
        Currently supports sending text and card messages.

        Args:
            msg: 要发送的 OpenBeMessage / the message to send
        """
        try:
            if msg.msg_type == MessageType.CARD:
                # content 应为 JSON 字符串或 dict / content should be JSON str or dict
                if isinstance(msg.content, str):
                    try:
                        card_content = json.loads(msg.content)
                    except json.JSONDecodeError:
                        # 降级为文本消息 / fall back to text
                        logger.warning(
                            "卡片内容解析失败，降级为文本 / "
                            "Card content parse failed, falling back to text."
                        )
                        self._api.send_text_message(msg.user_id, msg.content)
                        return
                else:
                    card_content = msg.content
                self._api.send_card_message(msg.user_id, card_content)
            else:
                # 默认发送文本 / default: send text
                self._api.send_text_message(msg.user_id, msg.content)

        except Exception as exc:  # pylint: disable=broad-except
            logger.exception(
                "通过飞书发送消息失败 / Failed to send message via Feishu: %s", exc
            )
            raise
