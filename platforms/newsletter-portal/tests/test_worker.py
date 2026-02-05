import pytest
from shared.connectors import get_connector, ConnectorError
from shared.connectors.base import FetchedItem


def test_get_connector_rss():
    """Test getting RSS connector"""
    connector = get_connector("rss", 1, "https://example.com/feed.xml")
    assert connector.__class__.__name__ == "RSSConnector"


def test_get_connector_youtube():
    """Test getting YouTube connector"""
    connector = get_connector("youtube_channel", 1, "https://youtube.com/channel/UCxxx")
    assert connector.__class__.__name__ == "YouTubeConnector"


def test_get_connector_website():
    """Test getting website connector"""
    connector = get_connector("website", 1, "https://example.com")
    assert connector.__class__.__name__ == "WebsiteConnector"


def test_get_connector_linkedin():
    """Test getting LinkedIn connector"""
    connector = get_connector("linkedin_profile", 1, "https://linkedin.com/in/user")
    assert connector.__class__.__name__ == "LinkedInConnector"


def test_get_connector_unknown():
    """Test getting unknown connector type"""
    with pytest.raises(ConnectorError):
        get_connector("unknown", 1, "https://example.com")


def test_fetched_item_creation():
    """Test creating FetchedItem"""
    item = FetchedItem(
        title="Test Title",
        url="https://example.com/article",
        content_text="Test content",
        author="Test Author"
    )
    
    assert item.title == "Test Title"
    assert item.url == "https://example.com/article"
    assert item.content_text == "Test content"
    assert item.author == "Test Author"


def test_base_connector_hash():
    """Test BaseConnector hash computation"""
    from shared.connectors.base import BaseConnector
    
    class TestConnector(BaseConnector):
        def fetch(self):
            return []
        def test_connection(self):
            return {"success": True}
    
    connector = TestConnector(1, "https://example.com")
    hash1 = connector._compute_hash("test content")
    hash2 = connector._compute_hash("test content")
    hash3 = connector._compute_hash("different content")
    
    assert hash1 == hash2
    assert hash1 != hash3
    assert len(hash1) == 32


def test_youtube_extract_channel_id():
    """Test YouTube channel ID extraction"""
    from shared.connectors.youtube import YouTubeConnector
    
    connector = YouTubeConnector(1, "")
    
    # Direct channel ID URL
    assert connector._extract_channel_id("https://youtube.com/channel/UCxxxxxxxxxxxxxxxxxxx") == "UCxxxxxxxxxxxxxxxxxxx"
    
    # With www
    assert connector._extract_channel_id("https://www.youtube.com/channel/UCxxx") == "UCxxx"


def test_rss_strip_html():
    """Test RSS HTML stripping"""
    from shared.connectors.rss import RSSConnector
    
    connector = RSSConnector(1, "")
    html = "<p>This is <strong>bold</strong> text</p>"
    text = connector._strip_html(html)
    
    assert "bold" in text
    assert "<p>" not in text
    assert "<strong>" not in text
