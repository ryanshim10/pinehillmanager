"""Source connectors for fetching content from various sources"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional, Dict, Any


@dataclass
class FetchedItem:
    """Item fetched from a source"""
    title: str
    url: str
    content_text: str
    content_html: Optional[str] = None
    published_at: Optional[datetime] = None
    author: Optional[str] = None
    content_hash: Optional[str] = None


class BaseConnector(ABC):
    """Base class for all source connectors"""
    
    def __init__(self, source_id: int, url: str, config: Optional[Dict[str, Any]] = None):
        self.source_id = source_id
        self.url = url
        self.config = config or {}
    
    @abstractmethod
    def fetch(self) -> List[FetchedItem]:
        """Fetch items from the source"""
        pass
    
    @abstractmethod
    def test_connection(self) -> Dict[str, Any]:
        """Test if the source is accessible"""
        pass
    
    def _compute_hash(self, content: str) -> str:
        """Compute a hash for content deduplication"""
        import hashlib
        return hashlib.sha256(content.encode("utf-8")).hexdigest()[:32]


class ConnectorError(Exception):
    """Connector-specific error"""
    pass
