import os
import json
import logging
from typing import Optional, List, Dict, Any
from dataclasses import dataclass

import requests

logger = logging.getLogger(__name__)


@dataclass
class LLMConfig:
    """LLM configuration from environment"""
    mode: str = "chat_completions"  # chat_completions | responses
    chat_url: str = ""
    responses_url: str = ""
    responses_model: str = "gpt-4o-mini"
    auth_type: str = "api-key"  # api-key | bearer
    api_key: str = ""
    api_key_header: str = "api-key"
    timeout_sec: int = 60
    max_tokens: int = 1200
    
    @classmethod
    def from_env(cls) -> "LLMConfig":
        """Create config from environment variables"""
        return cls(
            mode=os.environ.get("LLM_MODE", "chat_completions"),
            chat_url=os.environ.get("LLM_CHAT_URL", ""),
            responses_url=os.environ.get("LLM_RESPONSES_URL", ""),
            responses_model=os.environ.get("LLM_RESPONSES_MODEL", "gpt-4o-mini"),
            auth_type=os.environ.get("LLM_AUTH_TYPE", "api-key"),
            api_key=os.environ.get("LLM_API_KEY", ""),
            api_key_header=os.environ.get("LLM_API_KEY_HEADER", "api-key"),
            timeout_sec=int(os.environ.get("LLM_TIMEOUT_SEC", "60")),
            max_tokens=int(os.environ.get("LLM_MAX_TOKENS", "1200"))
        )
    
    @property
    def enabled(self) -> bool:
        """Check if LLM is configured"""
        if self.mode == "chat_completions":
            return bool(self.chat_url and self.api_key)
        else:
            return bool(self.responses_url and self.api_key)


class LLMClient:
    """Client for LLM API calls"""
    
    def __init__(self, config: Optional[LLMConfig] = None):
        self.config = config or LLMConfig.from_env()
    
    def _get_headers(self) -> Dict[str, str]:
        """Get request headers"""
        headers = {
            "Content-Type": "application/json"
        }
        
        if self.config.auth_type == "api-key":
            headers[self.config.api_key_header] = self.config.api_key
        elif self.config.auth_type == "bearer":
            headers["Authorization"] = f"Bearer {self.config.api_key}"
        
        return headers
    
    def generate(self, prompt: str, system_prompt: Optional[str] = None) -> Optional[str]:
        """Generate text using configured LLM"""
        if not self.config.enabled:
            logger.warning("LLM not configured")
            return None
        
        try:
            if self.config.mode == "chat_completions":
                return self._chat_completions(prompt, system_prompt)
            else:
                return self._responses(prompt, system_prompt)
        except Exception as e:
            logger.error(f"LLM generation error: {e}")
            return None
    
    def _chat_completions(self, prompt: str, system_prompt: Optional[str] = None) -> Optional[str]:
        """Use chat completions API"""
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        
        payload = {
            "messages": messages,
            "max_tokens": self.config.max_tokens,
            "temperature": 0.7
        }
        
        response = requests.post(
            self.config.chat_url,
            headers=self._get_headers(),
            json=payload,
            timeout=self.config.timeout_sec
        )
        response.raise_for_status()
        
        data = response.json()
        return data["choices"][0]["message"]["content"]
    
    def _responses(self, prompt: str, system_prompt: Optional[str] = None) -> Optional[str]:
        """Use responses API"""
        payload = {
            "model": self.config.responses_model,
            "input": prompt,
            "max_tokens": self.config.max_tokens
        }
        
        if system_prompt:
            payload["system"] = system_prompt
        
        response = requests.post(
            self.config.responses_url,
            headers=self._get_headers(),
            json=payload,
            timeout=self.config.timeout_sec
        )
        response.raise_for_status()
        
        data = response.json()
        return data.get("output", [{}])[0].get("content", [{}])[0].get("text")


class NewsletterGenerator:
    """Generate newsletters from items using LLM"""
    
    SYSTEM_PROMPT = """당신은 전문적인 뉴스레터 편집자입니다. 
제공된 기사들을 분석하여 간결하고 전문적인 뉴스레터를 작성하세요.

지침:
- 한국어로 작성
- 각 기사는 제목, 2-3문장 요약, 원문 링크 포함
- 중요도 순서로 정렬
- 총 길이는 500-1000단어
- HTML 형식으로 작성 (간단한 스타일링 포함)"""

    def __init__(self, llm_client: Optional[LLMClient] = None):
        self.llm = llm_client or LLMClient()
    
    def generate(
        self, 
        items: List[Dict[str, Any]], 
        subject_template: str = "[뉴스레터] 주간 업계 동향"
    ) -> Dict[str, str]:
        """
        Generate newsletter from items.
        
        Returns:
            Dict with 'subject', 'html', 'text'
        """
        if not items:
            return {
                "subject": subject_template,
                "html": "<p>선택된 기사가 없습니다.</p>",
                "text": "선택된 기사가 없습니다."
            }
        
        if not self.llm.config.enabled:
            # Fallback to simple aggregation
            return self._generate_simple(items, subject_template)
        
        # Build prompt
        items_text = "\n\n".join([
            f"[{i+1}] {item.get('title', 'Untitled')}\n"
            f"URL: {item.get('url', '')}\n"
            f"내용: {item.get('content_text', '')[:500]}..."
            for i, item in enumerate(items[:15])  # Limit to 15 items
        ])
        
        prompt = f"""다음 기사들을 분석하여 뉴스레터를 작성하세요:

{items_text}

다음 형식으로 HTML 뉴스레터를 작성:
1. 제목 (h1)
2. 소개 문단
3. 각 기사 섹션 (h2 제목, 요약 문단, "자세히 보기" 링크)
4. 마무리 문단

HTML만 출력."""
        
        try:
            html = self.llm.generate(prompt, self.SYSTEM_PROMPT)
            
            if not html:
                return self._generate_simple(items, subject_template)
            
            # Generate plain text version
            text = self._html_to_text(html)
            
            # Generate subject
            subject_prompt = f"다음 기사들의 주요 주제를 반영하여 뉴스레터 제목을 한 문장으로 작성:\n\n{items_text[:1000]}"
            subject = self.llm.generate(subject_prompt, "간결한 한국어 제목 작성")
            if not subject:
                subject = subject_template
            
            return {
                "subject": subject.strip().replace("[뉴스레터]", "").strip() or subject_template,
                "html": html,
                "text": text
            }
            
        except Exception as e:
            logger.error(f"Newsletter generation error: {e}")
            return self._generate_simple(items, subject_template)
    
    def _generate_simple(
        self, 
        items: List[Dict[str, Any]], 
        subject_template: str
    ) -> Dict[str, str]:
        """Generate simple newsletter without LLM"""
        html_parts = [
            "<!DOCTYPE html>",
            "<html><head><meta charset='UTF-8'></head><body>",
            f"<h1>{subject_template}</h1>",
            "<p>수집된 주요 기사입니다.</p>",
            "<hr>"
        ]
        
        text_parts = [subject_template, "", "수집된 주요 기사입니다.", ""]
        
        for item in items:
            title = item.get("title", "Untitled")
            url = item.get("url", "")
            content = item.get("content_text", "")[:200]
            
            html_parts.append(f"<h2><a href='{url}'>{title}</a></h2>")
            html_parts.append(f"<p>{content}...</p>")
            html_parts.append("<hr>")
            
            text_parts.append(f"## {title}")
            text_parts.append(f"URL: {url}")
            text_parts.append(content)
            text_parts.append("")
        
        html_parts.append("</body></html>")
        
        return {
            "subject": subject_template,
            "html": "\n".join(html_parts),
            "text": "\n".join(text_parts)
        }
    
    def _html_to_text(self, html: str) -> str:
        """Convert HTML to plain text"""
        try:
            from bs4 import BeautifulSoup
            soup = BeautifulSoup(html, "html.parser")
            
            # Replace links with text + URL
            for a in soup.find_all("a"):
                href = a.get("href", "")
                text = a.get_text(strip=True)
                if href and text:
                    a.replace_with(f"{text} ({href})")
            
            # Get text
            text = soup.get_text(separator="\n", strip=True)
            
            # Clean up
            lines = [line.strip() for line in text.split("\n") if line.strip()]
            return "\n\n".join(lines)
            
        except ImportError:
            # Fallback: strip tags
            import re
            text = re.sub(r"<[^>]+>", " ", html)
            return " ".join(text.split())
