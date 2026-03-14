"""
models.py — OpenBe Wings 通用消息模型
Universal message model for OpenBe Wings adapter service.

定义跨平台（飞书、企业微信）的统一消息数据结构，
确保不同平台的消息能以相同格式传递给 openbe-queen 处理。
"""

from dataclasses import dataclass, field
from enum import Enum


class PlatformType(Enum):
    """
    支持的平台类型枚举
    Supported platform types.
    """
    FEISHU = "feishu"    # 飞书 (Lark)
    WEWORK = "wework"    # 企业微信 (WeWork / WeCom)


class MessageType(Enum):
    """
    消息内容类型枚举
    Message content types supported across platforms.
    """
    TEXT = "text"    # 纯文本消息
    IMAGE = "image"  # 图片消息
    CARD = "card"    # 卡片/富文本消息（飞书交互卡片等）


@dataclass
class OpenBeMessage:
    """
    OpenBe 统一消息模型
    Universal message model used internally by OpenBe Wings.

    所有平台的消息经过适配器转换后均统一为此格式，
    再由 handler 转发给 openbe-queen 进行 AI 处理。

    Attributes:
        user_id    : 发送者在平台侧的唯一标识（飞书 open_id / 企业微信 UserId）
        platform   : 消息来源平台
        content    : 消息正文（图片消息时为媒体 ID 或 URL）
        msg_type   : 消息类型
        session_id : 会话/消息 ID，用于回复溯源；默认为空字符串
        raw_data   : 平台原始 payload，便于调试与扩展；默认为空字典
    """
    user_id: str
    platform: PlatformType
    content: str
    msg_type: MessageType
    session_id: str = ""
    raw_data: dict = field(default_factory=dict)

    def __repr__(self) -> str:
        # 隐藏 raw_data 以保持日志简洁 / hide raw_data to keep logs concise
        return (
            f"OpenBeMessage(user_id={self.user_id!r}, "
            f"platform={self.platform.value!r}, "
            f"msg_type={self.msg_type.value!r}, "
            f"session_id={self.session_id!r}, "
            f"content={self.content[:50]!r}{'...' if len(self.content) > 50 else ''})"
        )
