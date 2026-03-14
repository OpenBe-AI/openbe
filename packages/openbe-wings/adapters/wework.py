"""
adapters/wework.py — 企业微信 (WeWork / WeCom) 平台适配器
WeWork (WeCom) platform adapter for OpenBe Wings.

包含两个核心类：
  - WeWorkAPI    : 封装企业微信 REST API（含 access_token 缓存与过期重试）
  - WeWorkAdapter: 负责消息格式在企业微信原始 XML payload 与 OpenBeMessage 之间的互转

企业微信回调配置说明 / WeWork callback setup notes:
  1. 登录企业微信管理后台 → 应用管理 → 选择应用
  2. 进入「接收消息」→ 开启接收，填写接收消息服务器 URL：
         https://<your-domain>/webhook/wework
  3. 配置 Token 与 EncodingAESKey，填入 .env 对应字段
  4. 完成后点击「保存」，企业微信会向服务器发送 GET 请求验证签名
  5. 验证通过后，后续消息以 POST XML 格式推送

注：企业微信消息体为 XML 格式，需使用标准库解析。
Note: WeWork message body is in XML format, parsed with the standard library.
"""

import hashlib
import logging
import time
import xml.etree.ElementTree as ET
from typing import Optional
from urllib.parse import urlencode

import requests

from models import MessageType, OpenBeMessage, PlatformType
from config import Config

logger = logging.getLogger(__name__)

# 企业微信 API 基础地址 / WeWork API base URL
WEWORK_API_BASE = "https://qyapi.weixin.qq.com/cgi-bin"

# access_token 相关错误码 / Token-related error codes
WEWORK_ERR_TOKEN_EXPIRED = 42001   # access_token 过期
WEWORK_ERR_TOKEN_INVALID = 40014  # access_token 无效


class WeWorkAPI:
    """
    企业微信 API 客户端
    WeWork (WeCom) API client.

    提供 access_token 自动获取、缓存、过期重试，以及消息发送功能。
    Provides automatic access_token fetching, caching, retry on expiry,
    and message sending utilities.
    """

    def __init__(self, corp_id: str, corp_secret: str, agent_id: str) -> None:
        self._corp_id = corp_id
        self._corp_secret = corp_secret
        self._agent_id = agent_id

        # access_token 内存缓存 / in-memory token cache
        self._token: str = ""
        self._token_expires_at: float = 0.0  # Unix timestamp

    # ──────────────────────────────────────────────────────────────────────
    # Token 管理 / Token management
    # ──────────────────────────────────────────────────────────────────────

    def get_access_token(self, force_refresh: bool = False) -> str:
        """
        获取有效的企业微信 access_token，内置 300 秒缓冲区。
        Returns a valid WeWork access_token with a 300-second safety buffer.

        Args:
            force_refresh: 强制刷新忽略缓存 / force a refresh ignoring cache

        Returns:
            有效的 access_token 字符串 / valid access_token string

        Raises:
            RuntimeError: token 获取失败时 / on API failure
        """
        if not force_refresh and self._token and time.time() < self._token_expires_at:
            return self._token

        logger.info(
            "正在刷新企业微信 access_token / Refreshing WeWork access_token..."
        )
        url = f"{WEWORK_API_BASE}/gettoken"
        params = {
            "corpid": self._corp_id,
            "corpsecret": self._corp_secret,
        }

        try:
            resp = requests.get(url, params=params, timeout=10)
            resp.raise_for_status()
            data = resp.json()
        except requests.RequestException as exc:
            logger.error(
                "企业微信 token 请求失败 / WeWork token request failed: %s", exc
            )
            raise RuntimeError(f"Failed to fetch WeWork access_token: {exc}") from exc

        if data.get("errcode", 0) != 0:
            msg = data.get("errmsg", "unknown error")
            logger.error(
                "企业微信 token 接口错误 / WeWork token API error: errcode=%s errmsg=%s",
                data.get("errcode"),
                msg,
            )
            raise RuntimeError(f"WeWork token API error: {msg}")

        expires_in: int = data.get("expires_in", 7200)
        self._token = data["access_token"]
        # 提前 300 秒视为过期 / treat as expired 300s early
        self._token_expires_at = time.time() + expires_in - 300

        logger.info(
            "企业微信 access_token 刷新成功，有效期 %d 秒 / "
            "WeWork access_token refreshed, expires_in=%ds",
            expires_in,
        )
        return self._token

    # ──────────────────────────────────────────────────────────────────────
    # 消息发送 / Message sending
    # ──────────────────────────────────────────────────────────────────────

    def send_text_message(
        self, to_user: str, content: str, retry: bool = True
    ) -> dict:
        """
        向指定企业成员发送文本消息。
        Send a text message to the specified WeWork user.

        当接口返回 token 过期错误（42001）时自动刷新 token 并重试一次。
        Automatically refreshes token and retries once on errcode 42001.

        Args:
            to_user  : 接收者 UserId（多个用 | 分隔，@ all 时填 "@all"）
                       Recipient UserId; separate multiple with |; "@all" for all.
            content  : 消息正文 / message body text
            retry    : 是否允许 token 过期后重试 / allow one retry on token expiry

        Returns:
            企业微信 API 响应 JSON / WeWork API response dict

        Raises:
            RuntimeError: 发送失败时 / on send failure
        """
        url = f"{WEWORK_API_BASE}/message/send"
        params = {"access_token": self.get_access_token()}
        payload = {
            "touser": to_user,
            "msgtype": "text",
            "agentid": self._agent_id,
            "text": {"content": content},
        }

        try:
            resp = requests.post(url, params=params, json=payload, timeout=10)
            resp.raise_for_status()
            data = resp.json()
        except requests.RequestException as exc:
            logger.error(
                "企业微信发送消息请求失败 / WeWork send message request failed: %s", exc
            )
            raise RuntimeError(f"Failed to send WeWork text message: {exc}") from exc

        errcode = data.get("errcode", 0)

        # token 过期，刷新后重试一次 / token expired: refresh and retry once
        if errcode in (WEWORK_ERR_TOKEN_EXPIRED, WEWORK_ERR_TOKEN_INVALID) and retry:
            logger.warning(
                "企业微信 token 已过期（errcode=%d），刷新后重试 / "
                "WeWork token expired (errcode=%d), refreshing and retrying.",
                errcode,
                errcode,
            )
            self.get_access_token(force_refresh=True)
            return self.send_text_message(to_user, content, retry=False)

        if errcode != 0:
            errmsg = data.get("errmsg", "unknown error")
            logger.error(
                "企业微信发送消息接口错误 / WeWork send message error: "
                "errcode=%d errmsg=%s",
                errcode,
                errmsg,
            )
            raise RuntimeError(
                f"WeWork send message error: errcode={errcode} errmsg={errmsg}"
            )

        logger.debug(
            "企业微信文本消息发送成功 / WeWork text message sent to %s", to_user
        )
        return data


class WeWorkAdapter:
    """
    企业微信消息格式适配器
    WeWork message format adapter.

    负责：
      1. 服务器 URL 验证（GET 请求签名校验）
      2. 将企业微信原始 XML payload 转换为 OpenBeMessage
      3. 将 OpenBeMessage 通过企业微信 API 发送出去

    Responsibilities:
      1. Server URL verification (GET request signature check)
      2. Convert raw WeWork XML payload to OpenBeMessage
      3. Send OpenBeMessage back via WeWork API
    """

    def __init__(self, config: Optional[Config] = None) -> None:
        cfg = config or Config.from_env()
        self._token = cfg.wework_token
        self._encoding_aes_key = cfg.wework_encoding_aes_key
        self._api = WeWorkAPI(
            corp_id=cfg.wework_corp_id,
            corp_secret=cfg.wework_corp_secret,
            agent_id=cfg.wework_agent_id,
        )

    # ──────────────────────────────────────────────────────────────────────
    # 签名验证 / Signature verification
    # ──────────────────────────────────────────────────────────────────────

    def _compute_signature(
        self, token: str, timestamp: str, nonce: str, *extra: str
    ) -> str:
        """
        计算企业微信签名。
        Compute WeWork signature.

        算法：将 [token, timestamp, nonce, *extra] 排序后拼接，取 SHA1
        Algorithm: sort [token, timestamp, nonce, *extra], concat, SHA1.
        """
        pieces = sorted([token, timestamp, nonce, *extra])
        raw = "".join(pieces).encode("utf-8")
        return hashlib.sha1(raw).hexdigest()

    def verify_signature(
        self,
        msg_signature: str,
        timestamp: str,
        nonce: str,
        echostr: str = "",
    ) -> str:
        """
        验证企业微信服务器验证请求（GET /webhook/wework）。
        Verify WeWork server URL verification GET request.

        若签名合法，返回 echostr 以通过企业微信验证；否则返回空字符串。
        Returns echostr if signature is valid; otherwise returns empty string.

        Args:
            msg_signature: 企业微信传入的签名 / WeWork-provided signature
            timestamp    : 企业微信传入的时间戳 / WeWork-provided timestamp
            nonce        : 企业微信传入的随机字符串 / WeWork-provided nonce
            echostr      : 企业微信传入的随机字符串（GET 验证时使用）
                           Random string from WeWork (used during GET verification)

        Returns:
            echostr（签名合法）或 ""（签名非法）
            echostr (valid) or "" (invalid)
        """
        computed = self._compute_signature(self._token, timestamp, nonce)
        if computed == msg_signature:
            logger.info(
                "企业微信签名验证通过 / WeWork signature verification passed."
            )
            return echostr
        logger.warning(
            "企业微信签名验证失败 / WeWork signature verification failed. "
            "expected=%s, got=%s",
            computed,
            msg_signature,
        )
        return ""

    # ──────────────────────────────────────────────────────────────────────
    # 消息转换 / Message conversion
    # ──────────────────────────────────────────────────────────────────────

    def convert_to_openbe_msg(
        self, wework_payload: dict
    ) -> Optional[OpenBeMessage]:
        """
        将企业微信推送的 XML（已预解析为 dict）转换为 OpenBeMessage。
        Convert a pre-parsed WeWork XML payload (as dict) into an OpenBeMessage.

        调用方须先将原始 XML 解析为 dict，推荐结构如下：
        Callers must pre-parse the raw XML to a dict; recommended structure:
            {
                "FromUserName": "<UserId>",
                "MsgType": "text" | "image" | ...,
                "Content": "<text body>",       # for text
                "MediaId": "<media_id>",        # for image
                "MsgId": "<msg_id>",
                ...
            }

        Args:
            wework_payload: 已解析的企业微信消息 dict / parsed WeWork message dict

        Returns:
            转换后的 OpenBeMessage，失败时返回 None
        """
        try:
            user_id: str = wework_payload.get("FromUserName", "")
            raw_msg_type: str = wework_payload.get("MsgType", "text").lower()
            session_id: str = str(wework_payload.get("MsgId", ""))

            if raw_msg_type == "text":
                content = wework_payload.get("Content", "").strip()
                msg_type = MessageType.TEXT

            elif raw_msg_type == "image":
                # 图片消息：使用 MediaId 作为内容标识
                content = wework_payload.get("MediaId", "")
                msg_type = MessageType.IMAGE

            else:
                logger.info(
                    "企业微信消息类型 %r 暂不支持，透传原始内容 / "
                    "Unsupported WeWork message type %r, passing raw content.",
                    raw_msg_type,
                )
                content = wework_payload.get("Content", str(wework_payload))
                msg_type = MessageType.TEXT

            if not user_id:
                logger.warning(
                    "企业微信消息缺少 FromUserName / "
                    "WeWork message missing FromUserName."
                )
                return None

            return OpenBeMessage(
                user_id=user_id,
                platform=PlatformType.WEWORK,
                content=content,
                msg_type=msg_type,
                session_id=session_id,
                raw_data=wework_payload,
            )

        except Exception as exc:  # pylint: disable=broad-except
            logger.exception(
                "企业微信消息转换失败 / Failed to convert WeWork payload: %s", exc
            )
            return None

    def convert_from_openbe_msg(self, msg: OpenBeMessage) -> None:
        """
        将 OpenBeMessage 通过企业微信 API 发送给用户。
        Send an OpenBeMessage back to the user via the WeWork API.

        Args:
            msg: 要发送的 OpenBeMessage / the message to send
        """
        try:
            # 目前统一发送文本；卡片类型可扩展为图文消息
            # Currently sends text; card type can be extended to rich-text later.
            self._api.send_text_message(
                to_user=msg.user_id,
                content=msg.content,
            )
        except Exception as exc:  # pylint: disable=broad-except
            logger.exception(
                "通过企业微信发送消息失败 / Failed to send message via WeWork: %s", exc
            )
            raise

    # ──────────────────────────────────────────────────────────────────────
    # XML 辅助 / XML utilities
    # ──────────────────────────────────────────────────────────────────────

    @staticmethod
    def parse_xml(xml_body: bytes) -> dict:
        """
        将企业微信推送的 XML 原始字节解析为 dict。
        Parse raw WeWork XML bytes into a plain dict.

        Args:
            xml_body: 原始 XML 字节 / raw XML bytes

        Returns:
            解析结果 dict；解析失败时返回空 dict
        """
        try:
            root = ET.fromstring(xml_body.decode("utf-8"))
            return {child.tag: (child.text or "") for child in root}
        except ET.ParseError as exc:
            logger.error(
                "企业微信 XML 解析失败 / WeWork XML parse error: %s", exc
            )
            return {}
