import logging
from datetime import datetime
from typing import List, Dict, Any

import feedparser
import requests

from .base import BaseConnector, FetchedItem, ConnectorError

logger = logging.getLogger(__name__)


class RSSConnector(BaseConnector):
    """Connector for RSS/Atom feeds"""
    
    def __init__(self, source_id: int, url: str, config: Dict[str, Any] = None):
        super().__init__(source_id, url, config)
        self.timeout = self.config.get("timeout", 30)
        self.user_agent = self.config.get(
            "user_agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.0"
        )
    
    def fetch(self) -> List[FetchedItem]:
        """Fetch items from RSS feed"""
        try:
            # feedparser handles RSS/Atom feeds
            parsed = feedparser.parse(
                self.url,
                request_headers={"User-Agent": self.user_agent}
            )
            
            if parsed.bozo and hasattr(parsed, 'bozo_exception'):
                logger.warning(f"RSS parse warning for {self.url}: {parsed.bozo_exception}")
            
            items = []
            for entry in parsed.entries:
                item = self._parse_entry(entry)
                if item:
                    items.append(item)
            
            logger.info(f"RSS connector fetched {len(items)} items from {self.url}")
            return items
            
        except Exception as e:
            logger.error(f"RSS fetch error for {self.url}: {e}")
            raise ConnectorError(f"Failed to fetch RSS: {e}")
    
    def _parse_entry(self, entry) -> FetchedItem:
        """Parse a feed entry into FetchedItem"""
        # Get title
        title = entry.get("title", "Untitled")
        
        # Get URL
        url = entry.get("link", "")
        if not url:
            # Try alternate links
            if hasattr(entry, "links"):
                for link in entry.links:
                    if link.get("rel") == "alternate" or link.get("type") == "text/html":
                        url = link.get("href", "")
                        break
        
        # Get content
        content_text = ""
        content_html = None
        
        if hasattr(entry, "content") and entry.content:
            # Atom content
            content_html = entry.content[0].get("value", "")
            # Strip HTML for text version
            content_text = self._strip_html(content_html)
        elif hasattr(entry, "summary"):
            content_text = entry.summary
        elif hasattr(entry, "description"):
            content_text = entry.description
        
        # Get published date
        published_at = None
        if hasattr(entry, "published_parsed") and entry.published_parsed:
            published_at = datetime(*entry.published_parsed[:6])
        elif hasattr(entry, "updated_parsed") and entry.updated_parsed:
            published_at = datetime(*entry.updated_parsed[:6])
        
        # Get author
        author = entry.get("author", "")
        if not author and hasattr(entry, "authors"):
            authors = [a.get("name", "") for a in entry.authors if a.get("name")]
            author = ", ".join(authors)
        
        # Compute hash
        content_hash = self._compute_hash(f"{title}:{url}:{content_text[:200]}")
        
        return FetchedItem(
            title=title,
            url=url,
            content_text=content_text,
            content_html=content_html,
            published_at=published_at,
            author=author or None,
            content_hash=content_hash
        )
    
    def _strip_html(self, html: str) -> str:
        """Strip HTML tags from text"""
        try:
            from bs4 import BeautifulSoup
            soup = BeautifulSoup(html, "html.parser")
            return soup.get_text(separator=" ", strip=True)
        except ImportError:
            # Fallback: simple regex
            import re
            text = re.sub(r"<[^>]+>", " ", html)
            return " ".join(text.split())
    
    def test_connection(self) -> Dict[str, Any]:
        """Test RSS feed connection"""
        try:
            parsed = feedparser.parse(
                self.url,
                request_headers={"User-Agent": self.user_agent}
            )
            
            if not parsed.feed:
                return {
                    "success": False,
                    "error": "No feed data found",
                    "details": {"bozo": parsed.bozo}
                }
            
            return {
                "success": True,
                "feed_title": parsed.feed.get("title", "Unknown"),
                "entry_count": len(parsed.entries),
                "details": {
                    "version": parsed.version if hasattr(parsed, "version") else "unknown"
                }
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }
