import re
import logging
from datetime import datetime
from typing import List, Dict, Any
from urllib.parse import urlparse, parse_qs

import feedparser
import requests

from .base import BaseConnector, FetchedItem, ConnectorError

logger = logging.getLogger(__name__)


class YouTubeConnector(BaseConnector):
    """
    Connector for YouTube channels using RSS feeds.
    YouTube provides RSS feeds for channels at:
    https://www.youtube.com/feeds/videos.xml?channel_id=<CHANNEL_ID>
    """
    
    def __init__(self, source_id: int, url: str, config: Dict[str, Any] = None):
        super().__init__(source_id, url, config)
        self.timeout = self.config.get("timeout", 30)
    
    def _extract_channel_id(self, url: str) -> str:
        """Extract channel ID from various YouTube URL formats"""
        parsed = urlparse(url)
        
        # Format: /channel/UC...
        if "/channel/" in url:
            match = re.search(r"/channel/([a-zA-Z0-9_-]+)", url)
            if match:
                return match.group(1)
        
        # Format: /c/ or /@handle - need to resolve
        if "/c/" in url or "/@" in url:
            # Try to get channel page and extract ID
            return self._resolve_channel_id(url)
        
        # Format: ?channel_id=...
        query = parse_qs(parsed.query)
        if "channel_id" in query:
            return query["channel_id"][0]
        
        raise ConnectorError(f"Could not extract channel ID from URL: {url}")
    
    def _resolve_channel_id(self, url: str) -> str:
        """Resolve custom channel URL to channel ID"""
        try:
            headers = {
                "User-Agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/120.0.0.0 Safari/537.0"
                )
            }
            response = requests.get(url, headers=headers, timeout=self.timeout, allow_redirects=True)
            response.raise_for_status()
            
            # Look for channel ID in the page
            match = re.search(r'"channelId":"([a-zA-Z0-9_-]{24})"', response.text)
            if match:
                return match.group(1)
            
            # Alternative pattern
            match = re.search(r'<meta itemprop="channelId" content="([a-zA-Z0-9_-]+)">', response.text)
            if match:
                return match.group(1)
            
            raise ConnectorError("Could not find channel ID in page")
            
        except Exception as e:
            raise ConnectorError(f"Failed to resolve channel ID: {e}")
    
    def _get_rss_url(self, channel_id: str) -> str:
        """Get RSS feed URL for channel"""
        return f"https://www.youtube.com/feeds/videos.xml?channel_id={channel_id}"
    
    def fetch(self) -> List[FetchedItem]:
        """Fetch videos from YouTube channel"""
        try:
            channel_id = self._extract_channel_id(self.url)
            rss_url = self._get_rss_url(channel_id)
            
            logger.info(f"Fetching YouTube RSS: {rss_url}")
            
            parsed = feedparser.parse(rss_url)
            
            if parsed.bozo and hasattr(parsed, 'bozo_exception'):
                logger.warning(f"YouTube RSS warning: {parsed.bozo_exception}")
            
            items = []
            for entry in parsed.entries:
                item = self._parse_entry(entry)
                if item:
                    items.append(item)
            
            logger.info(f"YouTube connector fetched {len(items)} videos from channel {channel_id}")
            return items
            
        except Exception as e:
            logger.error(f"YouTube fetch error: {e}")
            raise ConnectorError(f"Failed to fetch YouTube: {e}")
    
    def _parse_entry(self, entry) -> FetchedItem:
        """Parse a YouTube feed entry"""
        title = entry.get("title", "Untitled")
        url = entry.get("link", "")
        
        # Get video description
        content_text = ""
        if hasattr(entry, "summary"):
            content_text = entry.summary
        elif hasattr(entry, "description"):
            content_text = entry.description
        
        # Get published date
        published_at = None
        if hasattr(entry, "published_parsed") and entry.published_parsed:
            published_at = datetime(*entry.published_parsed[:6])
        
        # Get author (channel name)
        author = entry.get("author", "")
        
        # YouTube entries have media:group with additional info
        media_thumbnail = None
        if hasattr(entry, "media_thumbnail") and entry.media_thumbnail:
            media_thumbnail = entry.media_thumbnail[0].get("url", "")
        
        # Compute hash
        content_hash = self._compute_hash(f"{title}:{url}")
        
        # Create HTML version with thumbnail
        content_html = f"""
        <div class="youtube-item">
            {f'<img src="{media_thumbnail}" alt="{title}" style="max-width:100%;">' if media_thumbnail else ''}
            <p>{content_text}</p>
            <p><a href="{url}" target="_blank">Watch on YouTube</a></p>
        </div>
        """
        
        return FetchedItem(
            title=title,
            url=url,
            content_text=content_text,
            content_html=content_html.strip(),
            published_at=published_at,
            author=author or None,
            content_hash=content_hash
        )
    
    def test_connection(self) -> Dict[str, Any]:
        """Test YouTube channel connection"""
        try:
            channel_id = self._extract_channel_id(self.url)
            rss_url = self._get_rss_url(channel_id)
            
            parsed = feedparser.parse(rss_url)
            
            if not parsed.feed:
                return {
                    "success": False,
                    "error": "No feed data found",
                    "channel_id": channel_id
                }
            
            return {
                "success": True,
                "channel_title": parsed.feed.get("title", "Unknown"),
                "channel_id": channel_id,
                "video_count": len(parsed.entries)
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }
