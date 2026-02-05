import json
import time
import logging
import random
from datetime import datetime
from typing import List, Dict, Any, Optional
from pathlib import Path

import requests

from .base import BaseConnector, FetchedItem, ConnectorError

logger = logging.getLogger(__name__)


class LinkedInConnector(BaseConnector):
    """
    Connector for LinkedIn profiles/companies.
    
    WARNING: LinkedIn has strict anti-scraping measures.
    This connector uses cookie-based authentication and rate limiting.
    Users must provide valid LinkedIn cookies in the config.
    
    Cookie file format (JSON):
    {
        "li_at": "your_li_at_cookie_value",
        "JSESSIONID": "your_jsessionid_value"
    }
    
    Rate limits:
    - Minimum 3 seconds between requests
    - Maximum 50 requests per hour
    - Random delays to avoid detection
    """
    
    # Rate limiting
    MIN_REQUEST_INTERVAL = 3  # seconds
    MAX_REQUESTS_PER_HOUR = 50
    JITTER_MIN = 0.5
    JITTER_MAX = 2.0
    
    def __init__(self, source_id: int, url: str, config: Dict[str, Any] = None):
        super().__init__(source_id, url, config)
        self.cookie_file = self.config.get("cookie_file")
        self.cookies = self._load_cookies()
        self.last_request_time = 0
        self.request_count = 0
        self.request_window_start = datetime.utcnow()
    
    def _load_cookies(self) -> Dict[str, str]:
        """Load cookies from file"""
        if not self.cookie_file:
            logger.warning("No cookie file specified for LinkedIn connector")
            return {}
        
        try:
            cookie_path = Path(self.cookie_file)
            if not cookie_path.exists():
                logger.error(f"Cookie file not found: {self.cookie_file}")
                return {}
            
            with open(cookie_path, "r") as f:
                cookies = json.load(f)
            
            # Validate required cookies
            if "li_at" not in cookies:
                logger.error("Missing required cookie: li_at")
                return {}
            
            logger.info(f"Loaded LinkedIn cookies from {self.cookie_file}")
            return cookies
            
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON in cookie file: {e}")
            return {}
        except Exception as e:
            logger.error(f"Error loading cookies: {e}")
            return {}
    
    def _apply_rate_limit(self):
        """Apply rate limiting delays"""
        now = time.time()
        
        # Check hourly limit
        hour_ago = (datetime.utcnow() - self.request_window_start).total_seconds() / 3600
        if hour_ago >= 1:
            # Reset window
            self.request_count = 0
            self.request_window_start = datetime.utcnow()
        
        if self.request_count >= self.MAX_REQUESTS_PER_HOUR:
            raise ConnectorError(
                f"LinkedIn hourly rate limit reached ({self.MAX_REQUESTS_PER_HOUR} requests)"
            )
        
        # Minimum interval between requests
        time_since_last = now - self.last_request_time
        if time_since_last < self.MIN_REQUEST_INTERVAL:
            sleep_time = self.MIN_REQUEST_INTERVAL - time_since_last
            # Add jitter
            sleep_time += random.uniform(self.JITTER_MIN, self.JITTER_MAX)
            logger.debug(f"Rate limiting: sleeping {sleep_time:.2f}s")
            time.sleep(sleep_time)
        
        self.last_request_time = time.time()
        self.request_count += 1
    
    def _get_headers(self) -> Dict[str, str]:
        """Get HTTP headers mimicking browser"""
        return {
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0.0.0 Safari/537.0"
            ),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Accept-Encoding": "gzip, deflate, br",
            "DNT": "1",
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "none",
            "Sec-Fetch-User": "?1",
            "Cache-Control": "max-age=0",
        }
    
    def _make_request(self, url: str) -> requests.Response:
        """Make rate-limited request"""
        self._apply_rate_limit()
        
        headers = self._get_headers()
        cookies = self.cookies
        
        try:
            response = requests.get(
                url,
                headers=headers,
                cookies=cookies,
                timeout=30,
                allow_redirects=True
            )
            response.raise_for_status()
            return response
            
        except requests.exceptions.HTTPError as e:
            if response.status_code == 999:
                raise ConnectorError(
                    "LinkedIn blocked request (999). Cookies may be expired or invalid."
                )
            elif response.status_code == 429:
                raise ConnectorError(
                    "LinkedIn rate limit exceeded (429). Please wait before retrying."
                )
            raise ConnectorError(f"HTTP error {response.status_code}: {e}")
            
        except Exception as e:
            raise ConnectorError(f"Request failed: {e}")
    
    def _extract_profile_id(self, url: str) -> str:
        """Extract profile/company ID from LinkedIn URL"""
        import re
        from urllib.parse import urlparse
        
        parsed = urlparse(url)
        path = parsed.path.strip("/")
        
        # Profile URL patterns
        patterns = [
            r"in/([^/]+)",  # /in/username
            r"company/([^/]+)",  # /company/name
        ]
        
        for pattern in patterns:
            match = re.search(pattern, path)
            if match:
                return match.group(1)
        
        raise ConnectorError(f"Could not extract LinkedIn ID from URL: {url}")
    
    def fetch(self) -> List[FetchedItem]:
        """
        Fetch posts from LinkedIn profile/company.
        
        NOTE: This is a stub implementation. Full LinkedIn scraping
        requires more complex handling due to JavaScript-rendered content.
        Consider using LinkedIn API for production use.
        """
        if not self.cookies:
            raise ConnectorError(
                "LinkedIn cookies not configured. Please set cookie_file in config."
            )
        
        try:
            profile_id = self._extract_profile_id(self.url)
            logger.info(f"Fetching LinkedIn profile: {profile_id}")
            
            # This is a stub - actual implementation would need to:
            # 1. Navigate to the activity/posts page
            # 2. Handle infinite scroll/pagination
            # 3. Parse post content (requires handling dynamically loaded content)
            
            # For now, return empty list with warning
            logger.warning(
                "LinkedIn connector is a stub. Full implementation requires "
                "Selenium/Playwright for JavaScript rendering or LinkedIn API access."
            )
            
            # Attempt basic profile page fetch to test cookies
            response = self._make_request(self.url)
            
            if "login" in response.url or "auth" in response.url:
                raise ConnectorError(
                    "LinkedIn redirected to login page. Cookies may be expired."
                )
            
            # Stub: return empty list
            return []
            
        except ConnectorError:
            raise
        except Exception as e:
            logger.error(f"LinkedIn fetch error: {e}")
            raise ConnectorError(f"Failed to fetch LinkedIn: {e}")
    
    def test_connection(self) -> Dict[str, Any]:
        """Test LinkedIn connection"""
        if not self.cookies:
            return {
                "success": False,
                "error": "Cookies not configured",
                "help": "Set 'cookie_file' in source config with path to cookies JSON"
            }
        
        try:
            response = self._make_request("https://www.linkedin.com/feed/")
            
            # Check if we're logged in
            if "feed" in response.url or "linkedin.com/in/" in response.url:
                return {
                    "success": True,
                    "message": "Successfully authenticated with LinkedIn",
                    "cookies_valid": True,
                    "rate_limit_info": {
                        "requests_this_hour": self.request_count,
                        "max_per_hour": self.MAX_REQUESTS_PER_HOUR,
                        "min_interval_seconds": self.MIN_REQUEST_INTERVAL
                    }
                }
            else:
                return {
                    "success": False,
                    "error": "Authentication check failed",
                    "current_url": response.url
                }
                
        except ConnectorError as e:
            return {
                "success": False,
                "error": str(e)
            }
        except Exception as e:
            return {
                "success": False,
                "error": f"Unexpected error: {e}"
            }
