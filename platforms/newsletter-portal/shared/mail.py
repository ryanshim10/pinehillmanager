import os
import smtplib
import logging
from email.message import EmailMessage
from email.utils import formatdate
from typing import Optional, List

logger = logging.getLogger(__name__)


class MailConfig:
    """Mail configuration from environment"""
    def __init__(self):
        self.host = os.environ.get("SMTP_HOST", "")
        self.port = int(os.environ.get("SMTP_PORT", "587"))
        self.starttls = (os.environ.get("SMTP_STARTTLS", "true").lower() 
                        in ("1", "true", "yes", "on"))
        self.ssl = (os.environ.get("SMTP_SSL", "false").lower() 
                   in ("1", "true", "yes", "on"))
        self.user = os.environ.get("SMTP_USER", "")
        self.password = os.environ.get("SMTP_PASS", "")
        self.from_email = os.environ.get("SMTP_FROM") or self.user
        self.enabled = bool(self.host)


class Mailer:
    """SMTP Mail sender"""
    
    def __init__(self, config: Optional[MailConfig] = None):
        self.config = config or MailConfig()
    
    def send(
        self,
        to_email: str,
        subject: str,
        body_text: str,
        body_html: Optional[str] = None,
        from_email: Optional[str] = None,
        cc: Optional[List[str]] = None,
        bcc: Optional[List[str]] = None
    ) -> dict:
        """
        Send an email
        
        Returns:
            dict with 'success' (bool) and 'error' (str or None)
        """
        if not self.config.enabled:
            logger.warning("SMTP not configured, email not sent")
            return {"success": False, "error": "SMTP not configured"}
        
        msg = EmailMessage()
        msg["Subject"] = subject
        msg["From"] = from_email or self.config.from_email
        msg["To"] = to_email
        msg["Date"] = formatdate(localtime=True)
        
        if cc:
            msg["Cc"] = ", ".join(cc)
        if bcc:
            msg["Bcc"] = ", ".join(bcc)
        
        # Set content
        if body_html:
            msg.add_alternative(body_html, subtype="html")
            msg.set_content(body_text)
        else:
            msg.set_content(body_text)
        
        try:
            if self.config.ssl:
                server = smtplib.SMTP_SSL(self.config.host, self.config.port, timeout=30)
            else:
                server = smtplib.SMTP(self.config.host, self.config.port, timeout=30)
            
            with server:
                if not self.config.ssl and self.config.starttls:
                    server.starttls()
                
                if self.config.user and self.config.password:
                    server.login(self.config.user, self.config.password)
                
                server.send_message(msg)
            
            logger.info(f"Email sent to {to_email}: {subject}")
            return {"success": True, "error": None}
            
        except Exception as e:
            logger.error(f"Failed to send email to {to_email}: {e}")
            return {"success": False, "error": str(e)}
    
    def send_temp_password(self, to_email: str, code: str) -> dict:
        """Send temporary password email"""
        subject = "[현대위아 뉴스레터 포탈] 임시비밀번호"
        
        text_body = f"""현대위아 뉴스레터 포탈 임시비밀번호 안내

임시비밀번호(숫자 6자리): {code}
유효기간: 30분

보안을 위해 로그인 후 즉시 비밀번호를 변경하세요.

본 메일은 발신 전용입니다.
"""
        
        html_body = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body {{ font-family: 'Malgun Gothic', sans-serif; line-height: 1.6; color: #333; }}
        .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
        .header {{ background: #003366; color: white; padding: 20px; text-align: center; }}
        .content {{ background: #f9f9f9; padding: 30px; margin: 20px 0; }}
        .code {{ font-size: 32px; font-weight: bold; color: #003366; 
                letter-spacing: 8px; text-align: center; padding: 20px;
                background: white; border: 2px solid #003366; margin: 20px 0; }}
        .footer {{ text-align: center; color: #666; font-size: 12px; margin-top: 30px; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>현대위아 뉴스레터 포탈</h1>
        </div>
        <div class="content">
            <h2>임시비밀번호 안내</h2>
            <p>아래의 임시비밀번호로 로그인하세요:</p>
            <div class="code">{code}</div>
            <p><strong>유효기간:</strong> 30분</p>
            <p style="color: #d9534f;">보안을 위해 로그인 후 즉시 비밀번호를 변경하세요.</p>
        </div>
        <div class="footer">
            <p>본 메일은 발신 전용입니다.</p>
            <p>© 현대위아</p>
        </div>
    </div>
</body>
</html>"""
        
        return self.send(to_email, subject, text_body, html_body)
    
    def send_newsletter(
        self,
        to_email: str,
        subject: str,
        html_content: str,
        text_content: str
    ) -> dict:
        """Send newsletter email"""
        return self.send(to_email, subject, text_content, html_content)


def get_mailer() -> Mailer:
    """Get configured mailer instance"""
    return Mailer()
