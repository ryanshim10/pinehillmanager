import re
import logging
from datetime import datetime
from typing import List, Dict, Any, Optional
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

from .base import BaseConnector, FetchedItem, ConnectorError

logger = logging.getLogger(__name__)


class WebsiteConnector(BaseConnector):
    """
    Connector for generic websites.
    Uses requests + readability-lxml for article extraction.
    Falls back to basic HTML parsing if readability fails.
    """
    
    def __init__(self, source_id: int, url: str, config: Dict[str, Any] = None):
        super().__init__(source_id, url, config)
        self.timeout = self.config.get("timeout", 30)
        self.max_pages = self.config.get("max_pages", 1)
        self.user_agent = self.config.get(
            "user_agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.0"
        )
        self.selector = self.config.get("selector")  # Optional CSS selector
        self.base_url = self.config.get("base_url") or self._get_base_url(url)
    
    def _get_base_url(self, url: str) -> str:
        """Extract base URL"""
        parsed = urlparse(url)
        return f"{parsed.scheme}://{parsed.netloc}"
    
    def _get_headers(self) -> Dict[str, str]:
        """Get HTTP headers"""
        return {
            "User-Agent": self.user_agent,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.5",
            "Accept-Encoding": "gzip, deflate, br",
            "DNT": "1",
            "Connection": "keep-alive",
        }
    
    def fetch(self) -> List[FetchedItem]:
        """Fetch articles from website"""
        try:
            items = []
            
            # If selector is specified, use it to find article links
            if self.selector:
                items = self._fetch_with_selector()
            else:
                # Try to extract single article
                item = self._fetch_single_article(self.url)
                if item:
                    items.append(item)
            
            logger.info(f"Website connector fetched {len(items)} items from {self.url}")
            return items
            
        except Exception as e:
            logger.error(f"Website fetch error for {self.url}: {e}")
            raise ConnectorError(f"Failed to fetch website: {e}")
    
    def _fetch_with_selector(self) -> List[FetchedItem]:
        """Fetch using CSS selector to find article links"""
        items = []
        
        try:
            response = requests.get(
                self.url, 
                headers=self._get_headers(), 
                timeout=self.timeout
            )
            response.raise_for_status()
            
            soup = BeautifulSoup(response.content, "html.parser")
            links = soup.select(self.selector)
            
            for link in links[:self.max_pages]:
                href = link.get("href", "")
                if not href:
                    continue
                
                # Resolve relative URLs
                article_url = urljoin(self.base_url, href)
                
                item = self._fetch_single_article(article_url)
                if item:
                    items.append(item)
            
            return items
            
        except Exception as e:
            logger.error(f"Error fetching with selector: {e}")
            return items
    
    def _fetch_single_article(self, url: str) -> Optional[FetchedItem]:
        """Fetch and parse a single article"""
        try:
            response = requests.get(
                url, 
                headers=self._get_headers(), 
                timeout=self.timeout
            )
            response.raise_for_status()
            
            # Try readability-lxml first
            title, content_text, content_html = self._extract_with_readability(response.text, url)
            
            # Fallback to BeautifulSoup if readability fails
            if not content_text:
                title, content_text, content_html = self._extract_with_bs4(response.text, url)
            
            if not content_text:
                return None
            
            # Try to extract date
            published_at = self._extract_date(response.text)
            
            # Compute hash
            content_hash = self._compute_hash(f"{title}:{url}:{content_text[:200]}")
            
            return FetchedItem(
                title=title or "Untitled",
                url=url,
                content_text=content_text,
                content_html=content_html,
                published_at=published_at,
                content_hash=content_hash
            )
            
        except Exception as e:
            logger.warning(f"Failed to fetch article {url}: {e}")
            return None
    
    def _extract_with_readability(self, html: str, url: str) -> tuple:
        """Extract content using readability-lxml"""
        try:
            from readability import Document
            
            doc = Document(html, url=url)
            title = doc.short_title()
            content_html = doc.summary()
            
            # Strip HTML for text version
            soup = BeautifulSoup(content_html, "html.parser")
            content_text = soup.get_text(separator="\n", strip=True)
            
            return title, content_text, content_html
            
        except ImportError:
            logger.warning("readability-lxml not installed, falling back to BeautifulSoup")
            return None, "", None
        except Exception as e:
            logger.warning(f"readability extraction failed: {e}")
            return None, "", None
    
    def _extract_with_bs4(self, html: str, url: str) -> tuple:
        """Extract content using BeautifulSoup as fallback"""
        soup = BeautifulSoup(html, "html.parser")
        
        # Get title
        title = ""
        title_tag = soup.find("title") or soup.find("h1")
        if title_tag:
            title = title_tag.get_text(strip=True)
        
        # Try to find main content
        content_text = ""
        content_html = ""
        
        # Common content selectors
        content_selectors = [
            "article",
            "[role='main']",
            ".content",
            ".post-content",
            ".entry-content",
            "main",
            "#content",
            ".article-body"
        ]
        
        for selector in content_selectors:
            content_elem = soup.select_one(selector)
            if content_elem:
                content_html = str(content_elem)
                content_text = content_elem.get_text(separator="\n", strip=True)
                break
        
        # Fallback to body if no content found
        if not content_text:
            body = soup.find("body")
            if body:
                # Remove script and style elements
                for elem in body.find_all(["script", "style", "nav", "header", "footer"]):
                    elem.decompose()
                content_text = body.get_text(separator="\n", strip=True)
                content_html = str(body)
        
        return title, content_text, content_html
    
    def _extract_date(self, html: str) -> Optional[datetime]:
        """Try to extract publication date from HTML"""
        try:
            # Look for common date patterns
            patterns = [
                r'<meta[^>]*property="article:published_time"[^>]*content="([^"]+)"',
                r'<meta[^>]*name="publish-date"[^>]*content="([^"]+)"',
                r'<meta[^>]*name="date"[^>]*content="([^"]+)"',
                r'<time[^>]*datetime="([^"]+)"',
                r'"datePublished"\s*:\s*"([^"]+)"',
            ]
            
            for pattern in patterns:
                match = re.search(pattern, html)
                if match:
                    date_str = match.group(1)
                    try:
                        # Try ISO format
                        return datetime.fromisoformat(date_str.replace("Z", "+00:00"))
                    except ValueError:
                        # Try other formats
                        from dateutil import parser
                        return parser.parse(date_str)
            
            return None
            
        except Exception:
            return None
    
    def test_connection(self) -> Dict[str, Any]:
        """Test website connection"""
        try:
            response = requests.get(
                self.url, 
                headers=self._get_headers(), 
                timeout=self.timeout
            )
            response.raise_for_status()
            
            soup = BeautifulSoup(response.content, "html.parser")
            title = soup.find("title")
            
            return {
                "success": True,
                "status_code": response.status_code,
                "page_title": title.get_text(strip=True) if title else "Unknown",
                "content_length": len(response.text),
                "selector_matches": len(soup.select(self.selector)) if self.selector else None
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }
