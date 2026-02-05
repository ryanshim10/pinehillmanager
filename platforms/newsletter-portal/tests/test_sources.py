import pytest
from shared.models import Source, SourceType


def test_create_source(client, user_token):
    """Test creating a source"""
    response = client.post(
        "/sources",
        json={
            "type": "rss",
            "name": "Test RSS Feed",
            "url": "https://example.com/feed.xml",
            "enabled": True
        },
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert "id" in data


def test_create_source_invalid_type(client, user_token):
    """Test creating source with invalid type"""
    response = client.post(
        "/sources",
        json={
            "type": "invalid_type",
            "name": "Test",
            "url": "https://example.com"
        },
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 400


def test_list_sources(client, user_token, test_user, session):
    """Test listing sources"""
    # Create some sources
    for i in range(3):
        source = Source(
            user_id=test_user.id,
            type=SourceType.RSS,
            name=f"Test Source {i}",
            url=f"https://example.com/{i}.xml",
            enabled=True
        )
        session.add(source)
    session.commit()
    
    response = client.get(
        "/sources",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert len(data["sources"]) == 3


def test_list_sources_filter_by_type(client, user_token, test_user, session):
    """Test listing sources with type filter"""
    # Create sources of different types
    source1 = Source(
        user_id=test_user.id,
        type=SourceType.RSS,
        name="RSS Source",
        url="https://example.com/rss.xml",
        enabled=True
    )
    source2 = Source(
        user_id=test_user.id,
        type=SourceType.YOUTUBE_CHANNEL,
        name="YouTube Source",
        url="https://youtube.com/channel/test",
        enabled=True
    )
    session.add(source1)
    session.add(source2)
    session.commit()
    
    response = client.get(
        "/sources?type=rss",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert len(data["sources"]) == 1
    assert data["sources"][0]["type"] == "rss"


def test_get_source(client, user_token, test_user, session):
    """Test getting source details"""
    source = Source(
        user_id=test_user.id,
        type=SourceType.RSS,
        name="Test Source",
        url="https://example.com/feed.xml",
        enabled=True
    )
    session.add(source)
    session.commit()
    session.refresh(source)
    
    response = client.get(
        f"/sources/{source.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["name"] == "Test Source"
    assert data["url"] == "https://example.com/feed.xml"


def test_get_source_not_found(client, user_token):
    """Test getting non-existent source"""
    response = client.get(
        "/sources/99999",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 404


def test_get_source_other_user(client, user_token, test_admin, session):
    """Test getting source belonging to another user"""
    source = Source(
        user_id=test_admin.id,
        type=SourceType.RSS,
        name="Admin's Source",
        url="https://example.com/admin.xml",
        enabled=True
    )
    session.add(source)
    session.commit()
    session.refresh(source)
    
    response = client.get(
        f"/sources/{source.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 404


def test_update_source(client, user_token, test_user, session):
    """Test updating source"""
    source = Source(
        user_id=test_user.id,
        type=SourceType.RSS,
        name="Old Name",
        url="https://example.com/old.xml",
        enabled=True
    )
    session.add(source)
    session.commit()
    session.refresh(source)
    
    response = client.patch(
        f"/sources/{source.id}",
        json={
            "name": "New Name",
            "url": "https://example.com/new.xml",
            "enabled": False
        },
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    
    # Verify update
    response = client.get(
        f"/sources/{source.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    data = response.json()
    assert data["name"] == "New Name"
    assert data["url"] == "https://example.com/new.xml"
    assert data["enabled"] is False


def test_delete_source(client, user_token, test_user, session):
    """Test deleting source"""
    source = Source(
        user_id=test_user.id,
        type=SourceType.RSS,
        name="To Delete",
        url="https://example.com/delete.xml",
        enabled=True
    )
    session.add(source)
    session.commit()
    session.refresh(source)
    
    response = client.delete(
        f"/sources/{source.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    
    # Verify deleted
    response = client.get(
        f"/sources/{source.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    assert response.status_code == 404


def test_create_source_with_config(client, user_token):
    """Test creating source with JSON config"""
    import json
    
    config = {"timeout": 60, "user_agent": "Custom Bot"}
    
    response = client.post(
        "/sources",
        json={
            "type": "website",
            "name": "Website with Config",
            "url": "https://example.com",
            "enabled": True,
            "config": json.dumps(config)
        },
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    
    source_id = response.json()["id"]
    
    # Verify config stored
    response = client.get(
        f"/sources/{source_id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    data = response.json()
    assert data["config"] == json.dumps(config)
