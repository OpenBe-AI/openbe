"""
config.py — OpenBe Wings 配置管理器
Singleton configuration manager reading from environment variables.

使用单例模式确保整个服务生命周期内配置只加载一次。
所有环境变量通过 python-dotenv 从 .env 文件或系统环境读取。

Usage:
    from config import Config
    cfg = Config.from_env()
    cfg.validate_feishu()
"""

import os
import logging
from typing import Optional

from dotenv import load_dotenv

logger = logging.getLogger(__name__)


class Config:
    """
    全局配置单例
    Global singleton configuration for OpenBe Wings.

    首次调用 from_env() 时从环境变量（或 .env 文件）加载配置，
    后续调用返回同一实例，避免重复 IO。
    """

    _instance: Optional["Config"] = None  # 单例实例 / singleton instance

    def __init__(self) -> None:
        # ── 飞书 (Feishu / Lark) 配置 ─────────────────────────────────────
        # 飞书开放平台应用凭证
        self.feishu_app_id: str = ""
        self.feishu_app_secret: str = ""
        # 用于验证事件回调真实性的 token
        self.feishu_verification_token: str = ""
        # 消息加密密钥（可选，不启用加密时留空）
        self.feishu_encrypt_key: str = ""

        # ── 企业微信 (WeWork / WeCom) 配置 ────────────────────────────────
        self.wework_corp_id: str = ""
        self.wework_corp_secret: str = ""
        # 接收消息服务器配置中的 Token
        self.wework_token: str = ""
        # 消息加解密密钥（43 位）
        self.wework_encoding_aes_key: str = ""
        # 应用 AgentId
        self.wework_agent_id: str = ""

        # ── 通用配置 ──────────────────────────────────────────────────────
        # openbe-queen 服务地址
        self.openbe_queen_url: str = "http://localhost:8080"
        # 默认 hive ID，对应 queen 中的对话实例
        self.openbe_hive_id: str = "default"
        # Wings 服务监听端口
        self.wings_port: int = 8081

    # ──────────────────────────────────────────────────────────────────────
    # 单例工厂方法 / Singleton factory
    # ──────────────────────────────────────────────────────────────────────

    @classmethod
    def from_env(cls, env_file: str = ".env") -> "Config":
        """
        从环境变量（或 .env 文件）加载配置并返回单例实例。
        Load configuration from environment variables (or .env file)
        and return the singleton instance.

        Args:
            env_file: .env 文件路径，默认为当前目录下的 .env
                      Path to the .env file; defaults to ".env" in cwd.

        Returns:
            Config 单例实例 / singleton Config instance
        """
        if cls._instance is not None:
            # 已经初始化，直接返回 / already initialised, return cached
            return cls._instance

        # 加载 .env 文件（若存在）；不存在时静默忽略
        # Load .env file if present; silently ignore if missing.
        load_dotenv(dotenv_path=env_file, override=False)

        instance = cls()

        # ── 飞书配置读取 ───────────────────────────────────────────────
        instance.feishu_app_id = os.getenv("FEISHU_APP_ID", "")
        instance.feishu_app_secret = os.getenv("FEISHU_APP_SECRET", "")
        instance.feishu_verification_token = os.getenv(
            "FEISHU_VERIFICATION_TOKEN", ""
        )
        instance.feishu_encrypt_key = os.getenv("FEISHU_ENCRYPT_KEY", "")

        # ── 企业微信配置读取 ───────────────────────────────────────────
        instance.wework_corp_id = os.getenv("WEWORK_CORP_ID", "")
        instance.wework_corp_secret = os.getenv("WEWORK_CORP_SECRET", "")
        instance.wework_token = os.getenv("WEWORK_TOKEN", "")
        instance.wework_encoding_aes_key = os.getenv(
            "WEWORK_ENCODING_AES_KEY", ""
        )
        instance.wework_agent_id = os.getenv("WEWORK_AGENT_ID", "")

        # ── 通用配置读取 ───────────────────────────────────────────────
        instance.openbe_queen_url = os.getenv(
            "OPENBE_QUEEN_URL", "http://localhost:8080"
        ).rstrip("/")  # 去掉末尾斜杠，避免路径拼接出现双斜杠
        instance.openbe_hive_id = os.getenv("OPENBE_HIVE_ID", "default")

        try:
            instance.wings_port = int(os.getenv("WINGS_PORT", "8081"))
        except ValueError:
            logger.warning(
                "WINGS_PORT 不是有效整数，使用默认值 8081 / "
                "WINGS_PORT is not a valid integer, falling back to 8081."
            )
            instance.wings_port = 8081

        cls._instance = instance
        logger.info(
            "Wings 配置加载完成 / Wings config loaded. "
            "queen_url=%s, port=%d, hive=%s",
            instance.openbe_queen_url,
            instance.wings_port,
            instance.openbe_hive_id,
        )
        return instance

    # ──────────────────────────────────────────────────────────────────────
    # 校验方法 / Validation helpers
    # ──────────────────────────────────────────────────────────────────────

    def validate_feishu(self) -> None:
        """
        校验飞书必填配置项。
        Validate that all required Feishu fields are present.

        Raises:
            ValueError: 当任意必填项为空时 / if any required field is empty.
        """
        required = {
            "FEISHU_APP_ID": self.feishu_app_id,
            "FEISHU_APP_SECRET": self.feishu_app_secret,
            "FEISHU_VERIFICATION_TOKEN": self.feishu_verification_token,
        }
        missing = [k for k, v in required.items() if not v]
        if missing:
            raise ValueError(
                f"飞书配置缺少必填项 / Missing required Feishu config: "
                f"{', '.join(missing)}"
            )
        logger.debug("飞书配置校验通过 / Feishu config validation passed.")

    def validate_wework(self) -> None:
        """
        校验企业微信必填配置项。
        Validate that all required WeWork fields are present.

        Raises:
            ValueError: 当任意必填项为空时 / if any required field is empty.
        """
        required = {
            "WEWORK_CORP_ID": self.wework_corp_id,
            "WEWORK_CORP_SECRET": self.wework_corp_secret,
            "WEWORK_TOKEN": self.wework_token,
            "WEWORK_ENCODING_AES_KEY": self.wework_encoding_aes_key,
            "WEWORK_AGENT_ID": self.wework_agent_id,
        }
        missing = [k for k, v in required.items() if not v]
        if missing:
            raise ValueError(
                f"企业微信配置缺少必填项 / Missing required WeWork config: "
                f"{', '.join(missing)}"
            )
        logger.debug("企业微信配置校验通过 / WeWork config validation passed.")

    # ──────────────────────────────────────────────────────────────────────
    # 辅助方法 / Utility helpers
    # ──────────────────────────────────────────────────────────────────────

    @classmethod
    def reset(cls) -> None:
        """
        重置单例（主要用于单元测试）。
        Reset the singleton instance (primarily for unit tests).
        """
        cls._instance = None

    def __repr__(self) -> str:
        return (
            f"Config(feishu_app_id={self.feishu_app_id!r}, "
            f"wework_corp_id={self.wework_corp_id!r}, "
            f"queen_url={self.openbe_queen_url!r}, "
            f"wings_port={self.wings_port})"
        )
