"""
services/auth_service.py
深大CAS SSO统一身份认证服务

职责：
  1. 模拟CAS登录流程（GET登录页 → 提取lt/execution → POST → 处理302回调）
  2. 验证码策略：先试无验证码登录，失败则提示手动输入
  3. Cookie持久化到 cookie_dir/{site_name}.json
  4. 过期检测：403或重定向到登录页时自动重新登录
  5. 凭据从 .env 读取 SZU_USERNAME / SZU_PASSWORD
"""

import os
import json
import re
import time
import random
import base64
from pathlib import Path
from urllib.parse import urljoin, urlparse, parse_qs

import httpx
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

# 注意：main.py 入口处已调用 load_dotenv()，此处不再重复
# 但为确保独立运行时也能加载环境变量，保留安全调用
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass


class CASAuthService:
    """深大CAS SSO认证服务，为公文通Spider提供登录态Cookie。"""

    def __init__(self, site_config: dict, cookie_dir: str = "data/cookies"):
        """
        Args:
            site_config: sites.yaml 中该站点的 auth 配置块
            cookie_dir:   Cookie持久化目录
        """
        auth_cfg = site_config.get("auth", {})
        self.login_url = auth_cfg["login_url"]          # CAS登录页
        self.service_url = auth_cfg["service_url"]       # 回调service
        self.captcha_fallback = auth_cfg.get("captcha_fallback", "manual")

        # 从 .env 读取凭据
        cred_env = auth_cfg.get("credentials_env", {})
        self.username = os.getenv(cred_env.get("username", "SZU_USERNAME"), "")
        self.password = os.getenv(cred_env.get("password", "SZU_PASSWORD"), "")

        if not self.username or not self.password:
            raise ValueError(
                "缺少CAS凭据，请在 .env 中设置 SZU_USERNAME 和 SZU_PASSWORD"
            )

        # Cookie持久化路径（使用JSON替代pickle，消除代码注入风险）
        site_name = site_config.get("name", "gwt")
        self.cookie_file = Path(cookie_dir) / f"{site_name}.json"
        self.cookie_file.parent.mkdir(parents=True, exist_ok=True)

        # httpx 客户端（跟随重定向由代码手动处理）
        self.client = httpx.Client(
            timeout=30,
            follow_redirects=False,   # 手动处理302以捕获Cookie
            verify=False,             # 校内证书可能自签名
        )

    # ------------------------------------------------------------------ #
    #  公开接口
    # ------------------------------------------------------------------ #

    def get_cookies(self) -> dict:
        """
        获取已认证的Cookie字典。

        优先加载本地持久化的Cookie；若过期或不存在，则重新登录。
        """
        cookies = self._load_cookies()
        if cookies and self._check_cookie_valid(cookies):
            return cookies

        print("[Auth] Cookie无效或已过期，重新登录...")
        return self._login()

    # ------------------------------------------------------------------ #
    #  Cookie 持久化
    # ------------------------------------------------------------------ #

    def _load_cookies(self) -> dict | None:
        """从 JSON 文件加载Cookie。"""
        if not self.cookie_file.exists():
            return None
        try:
            with open(self.cookie_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            # 简单校验：文件应该是 dict
            if isinstance(data, dict) and data:
                print(f"[Auth] 从 {self.cookie_file} 加载Cookie成功")
                return data
        except Exception as e:
            print(f"[Auth] 加载Cookie失败: {e}")
        return None

    def _save_cookies(self, cookies: dict):
        """持久化Cookie到 JSON 文件。"""
        with open(self.cookie_file, "w", encoding="utf-8") as f:
            json.dump(cookies, f, ensure_ascii=False, indent=2)
        print(f"[Auth] Cookie已保存到 {self.cookie_file}")

    # ------------------------------------------------------------------ #
    #  Cookie 有效性检测
    # ------------------------------------------------------------------ #

    def _check_cookie_valid(self, cookies: dict) -> bool:
        """
        检测Cookie是否仍然有效。

        策略：用已有Cookie请求公文通首页，若返回302到CAS登录页则判定过期。
        """
        try:
            resp = self.client.get(
                self.service_url,
                cookies=cookies,
            )
            # 200 = 正常访问，Cookie有效
            if resp.status_code == 200:
                return True
            # 302 且重定向到 authserver = Cookie过期
            if resp.status_code in (301, 302, 303, 307):
                redirect_url = resp.headers.get("location", "")
                if "authserver" in redirect_url:
                    print("[Auth] Cookie已过期（被重定向到登录页）")
                    return False
            # 403 = 权限不足，也视为需要重新登录
            if resp.status_code == 403:
                print("[Auth] Cookie已过期（403 Forbidden）")
                return False
            # 其他状态码（500/502等），保守处理：视为Cookie失效，触发重新登录
            print(f"[Auth] Cookie检测异常状态码 {resp.status_code}，视为失效")
            return False
        except Exception as e:
            print(f"[Auth] Cookie检测异常: {e}")
            # 异常时默认返回False，触发重新登录
            return False

    # ------------------------------------------------------------------ #
    #  CAS 登录流程
    # ------------------------------------------------------------------ #

    def _login(self) -> dict:
        """
        执行完整的CAS登录流程：

        1. GET 登录页 → 提取 lt / execution 等隐藏字段
        2. POST 提交表单（先不带验证码）
        3. 若返回验证码错误 → 进入验证码回退流程
        4. 处理302回调 → 获取最终session cookie
        5. 持久化Cookie
        """
        # Step 1: GET 登录页，提取隐藏字段
        login_page_url = f"{self.login_url}?service={self.service_url}"
        resp = self.client.get(login_page_url)
        resp.raise_for_status()
        html = resp.text

        lt, execution = self._extract_hidden_fields(html)
        if not execution:
            raise RuntimeError(
                f"无法从登录页提取 execution 字段，"
                f"页面长度={len(html)}，请检查登录页结构是否变化"
            )
        # lt 可能为空字符串（深大CAS已不再使用该字段），不作为错误条件
        if not lt:
            print("[Auth] lt 字段为空，将使用空值提交（深大CAS已弃用该字段）")

        # Step 1.5: 提取密码加密盐值并加密密码
        encrypted_pwd = self._encrypt_password(html, self.password)

        # Step 2: 构造POST表单
        form_data = {
            "username": self.username,
            "password": encrypted_pwd,
            "lt": lt,
            "dllt": "generalLoginSSL",
            "execution": execution,
            "_eventId": "submit",
            "rmShown": "1",
        }

        # Step 3: 先尝试无验证码提交
        print("[Auth] 尝试无验证码登录...")
        resp = self.client.post(
            login_page_url,
            data=form_data,
            cookies=resp.cookies,
        )

        # Step 4: 判断是否需要验证码
        if self._is_captcha_required(resp):
            print("[Auth] 需要验证码，进入验证码处理流程...")
            resp = self._handle_captcha(resp, form_data)

        # Step 5: 处理登录结果，跟踪302回调
        cookies = self._process_login_response(resp)
        self._save_cookies(cookies)
        return cookies

    def _encrypt_password(self, html: str, password: str) -> str:
        """
        使用登录页中的 pwdEncryptSalt 对密码进行 AES-CBC 加密。

        深大CAS加密逻辑（来自 encrypt.js）：
          encryptedPwd = encryptAES(password, pwdEncryptSalt)
          其中 encryptAES = AES-CBC(randomString(64) + password, salt, randomString(16))
        """
        # 提取 salt
        salt_match = re.search(
            r'id=["\']pwdEncryptSalt["\'][^>]*value=["\']([^"\']+)["\']', html
        )
        if not salt_match:
            print("[Auth] 未找到 pwdEncryptSalt，密码不加密")
            return password

        salt = salt_match.group(1)
        # 生成随机字符串（与 JS 中 $aes_chars 一致）
        aes_chars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
        random_prefix = "".join(random.choice(aes_chars) for _ in range(64))
        random_iv = "".join(random.choice(aes_chars) for _ in range(16))

        # AES-CBC 加密（PKCS7 padding）
        key = salt.encode("utf-8")
        iv = random_iv.encode("utf-8")
        plaintext = (random_prefix + password).encode("utf-8")

        # PKCS7 padding
        block_size = 16
        pad_len = block_size - (len(plaintext) % block_size)
        plaintext += bytes([pad_len] * pad_len)

        cipher = Cipher(algorithms.AES(key), modes.CBC(iv))
        encryptor = cipher.encryptor()
        encrypted = encryptor.update(plaintext) + encryptor.finalize()
        encrypted_b64 = base64.b64encode(encrypted).decode("utf-8")

        print(f"[Auth] 密码已AES加密，salt长度={len(salt)}")
        return encrypted_b64

    def _extract_hidden_fields(self, html: str) -> tuple[str, str]:
        """
        从CAS登录页HTML中提取 lt 和 execution 隐藏字段。

        深大CAS的表单结构（2026年更新）：
          <input type="hidden" name="lt" id="lt" value="" />
          <input type="hidden" name="execution" value="c098b471-..." />
        注意：lt 字段 value 可能为空，这是正常的，不再作为登录失败条件。
        """
        # lt: 兼容 id 在 name 前面、value 可能为空
        lt_match = re.search(
            r'name=["\']lt["\'][^>]*value=["\']([^"\']*)["\']', html
        ) or re.search(
            r'value=["\']([^"\']*)["\'][^>]*name=["\']lt["\']', html
        ) or re.search(
            r'<input[^>]*id=["\']lt["\'][^>]*value=["\']([^"\']*)["\']', html
        )
        # execution: 长UUID格式，兼容多种属性顺序
        exec_match = re.search(
            r'name=["\']execution["\'][^>]*value=["\']([^"\']+)["\']', html
        ) or re.search(
            r'value=["\']([^"\']+)["\'][^>]*name=["\']execution["\']', html
        )

        lt = lt_match.group(1) if lt_match else ""
        execution = exec_match.group(1) if exec_match else ""
        return lt, execution

    def _is_captcha_required(self, resp: httpx.Response) -> bool:
        """
        判断登录响应是否提示需要验证码。

        常见判断依据：
          - 响应HTML中包含验证码相关提示文本
          - 响应中包含验证码图片元素
        """
        if resp.status_code == 200:
            html = resp.text
            captcha_keywords = [
                "验证码不能为空",
                "captcha",
                "验证码错误",
                "needCaptcha",
                "randCode",
            ]
            return any(kw in html for kw in captcha_keywords)
        return False

    def _handle_captcha(
        self,
        resp: httpx.Response,
        form_data: dict,
    ) -> httpx.Response:
        """
        处理验证码：根据 captcha_fallback 配置决定策略。

        策略：
          - "abort"（推荐）: 抛出异常、记告警日志、跳过本次爬取
          - "manual": 仅在CLI交互模式下可用，input()在服务进程不可用
          - 未来可集成OCR自动识别（如ddddocr）

        Args:
            resp: 上一次POST登录的响应（包含验证码的页面）
            form_data: 已构造的表单数据
        """
        # 推荐策略：abort — 安全跳过，不阻塞服务
        if self.captcha_fallback == "abort":
            import logging
            logging.getLogger(__name__).warning(
                "CAS登录触发验证码，abort策略：跳过本次爬取。"
                "如需处理验证码，可将 captcha_fallback 改为 'manual'（仅CLI模式）"
            )
            raise RuntimeError(
                "CAS登录需要验证码，abort策略下跳过本次爬取"
            )

        if self.captcha_fallback != "manual":
            raise RuntimeError(
                f"CAS登录需要验证码，但不支持的回退策略: {self.captcha_fallback}"
            )

        # manual策略：仅在CLI交互模式可用（input()在FastAPI服务进程不可用）
        import sys
        if not sys.stdin.isatty():
            raise RuntimeError(
                "CAS登录需要验证码，但当前非交互模式（服务进程），无法手动输入。"
                "请将 captcha_fallback 改为 'abort' 或集成OCR识别"
            )

        # 重新GET登录页以获取新的lt/execution和验证码
        login_page_url = f"{self.login_url}?service={self.service_url}"
        resp = self.client.get(login_page_url)
        html = resp.text
        lt, execution = self._extract_hidden_fields(html)

        # 下载验证码图片
        captcha_url = self._extract_captcha_url(html)
        if not captcha_url:
            captcha_url = f"{self.login_url}/captcha.html"
            if "randCode" in html:
                captcha_url = f"{self.login_url}/randCode"

        captcha_path = Path("data/cookies/captcha.png")
        captcha_path.parent.mkdir(parents=True, exist_ok=True)

        print(f"[Auth] 下载验证码: {captcha_url}")
        captcha_resp = self.client.get(captcha_url, cookies=resp.cookies)
        with open(captcha_path, "wb") as f:
            f.write(captcha_resp.content)
        print(f"[Auth] 验证码已保存到 {captcha_path}")

        captcha_code = input("[Auth] 请打开验证码图片，输入验证码: ").strip()
        if not captcha_code:
            raise RuntimeError("验证码为空，登录终止")

        form_data.update({
            "lt": lt,
            "execution": execution,
            "captcha": captcha_code,
            "randCode": captcha_code,
        })

        return self.client.post(
            login_page_url,
            data=form_data,
            cookies=resp.cookies,
        )

    def _extract_captcha_url(self, html: str) -> str:
        """从登录页HTML中提取验证码图片URL。"""
        patterns = [
            r'<img[^>]+id="captcha"[^>]+src="([^"]+)"',
            r'<img[^>]+src="([^"]+)"[^>]+id="captcha"',
            r'<img[^>]+src="([^"]*(?:captcha|randCode)[^"]*)"',
            r'<img[^>]+class="captcha-img"[^>]+src="([^"]+)"',
        ]
        for pattern in patterns:
            match = re.search(pattern, html)
            if match:
                src = match.group(1)
                # 处理相对路径
                if not src.startswith("http"):
                    src = urljoin(self.login_url, src)
                return src
        return ""

    def _process_login_response(self, resp: httpx.Response) -> dict:
        """
        处理CAS登录后的响应，跟踪302回调链，收集最终Cookie。

        CAS登录成功后的典型流程：
          POST /authserver/login → 302 → GET service_url?ticket=ST-xxx
          → 302 → GET service_url（设置session cookie）→ 200

        返回最终在公文通域名下的全部Cookie。
        """
        # 收集所有Cookie
        all_cookies = {}
        for name, value in resp.cookies.items():
            all_cookies[name] = value

        # 登录失败的检测
        if resp.status_code == 200:
            html = resp.text
            error_keywords = [
                "用户名或密码错误",
                "您提供的用户名或者密码有误",
                "Invalid credentials",
                "账号或密码错误",
                "Authentication Failure",
            ]
            for kw in error_keywords:
                if kw in html:
                    raise RuntimeError(f"CAS登录失败: {kw}")

        # 跟踪302重定向链（最多10跳）
        max_redirects = 10
        for _ in range(max_redirects):
            if resp.status_code not in (301, 302, 303, 307):
                break

            redirect_url = resp.headers.get("location", "")
            if not redirect_url:
                break

            # 处理相对URL
            if not redirect_url.startswith("http"):
                redirect_url = urljoin(str(resp.url), redirect_url)

            # 收集跳转中的Cookie
            for name, value in resp.cookies.items():
                all_cookies[name] = value

            # 检查是否已回到目标服务域名（登录成功标志）
            parsed = urlparse(redirect_url)
            service_host = urlparse(self.service_url).hostname or ""
            if parsed.hostname and (
                parsed.hostname == service_host
                or parsed.hostname.endswith(f".{service_host}")
            ):
                # 这是回传ticket的关键请求
                resp = self.client.get(
                    redirect_url,
                    cookies=all_cookies,
                )
                for name, value in resp.cookies.items():
                    all_cookies[name] = value
                # 再跟一次最终跳转
                if resp.status_code in (301, 302, 303, 307):
                    final_url = resp.headers.get("location", "")
                    if final_url and not final_url.startswith("http"):
                        final_url = urljoin(str(resp.url), final_url)
                    if final_url:
                        resp = self.client.get(
                            final_url,
                            cookies=all_cookies,
                        )
                        for name, value in resp.cookies.items():
                            all_cookies[name] = value
                break
            else:
                # 仍在CAS域内，继续跟踪
                resp = self.client.get(
                    redirect_url,
                    cookies=all_cookies,
                )

        if not all_cookies:
            raise RuntimeError("CAS登录流程完成但未获取到任何Cookie")

        print(f"[Auth] 登录成功，获取到 {len(all_cookies)} 个Cookie")
        return all_cookies

    # ------------------------------------------------------------------ #
    #  资源清理
    # ------------------------------------------------------------------ #

    def close(self):
        """关闭httpx客户端。"""
        self.client.close()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
