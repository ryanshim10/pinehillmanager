import pytest
from shared.models import Newsletter, NewsletterStatus


def test_create_newsletter(client, user_token):
    """Test creating manual newsletter"""
    response = client.post(
        "/newsletters",
        json={
            "subject": "Test Newsletter",
            "html": "<h1>Test</h1><p>Content</p>",
            "text": "Test\n\nContent",
            "item_ids": []
        },
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert "id" in data


def test_list_newsletters(client, user_token, test_user, session):
    """Test listing newsletters"""
    # Create newsletters
    for i in range(3):
        newsletter = Newsletter(
            user_id=test_user.id,
            status=NewsletterStatus.DRAFT,
            subject=f"Newsletter {i}",
            html=f"<p>Content {i}</p>",
            text=f"Content {i}",
            item_count=0
        )
        session.add(newsletter)
    session.commit()
    
    response = client.get(
        "/newsletters",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert len(data["newsletters"]) == 3


def test_get_newsletter(client, user_token, test_user, session):
    """Test getting newsletter details"""
    newsletter = Newsletter(
        user_id=test_user.id,
        status=NewsletterStatus.DRAFT,
        subject="Test Newsletter",
        html="<p>HTML Content</p>",
        text="Text Content",
        item_count=5
    )
    session.add(newsletter)
    session.commit()
    session.refresh(newsletter)
    
    response = client.get(
        f"/newsletters/{newsletter.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["subject"] == "Test Newsletter"
    assert data["html"] == "<p>HTML Content</p>"
    assert data["item_count"] == 5


def test_delete_newsletter(client, user_token, test_user, session):
    """Test deleting newsletter"""
    newsletter = Newsletter(
        user_id=test_user.id,
        status=NewsletterStatus.DRAFT,
        subject="To Delete",
        html="<p>Delete me</p>",
        text="Delete me",
        item_count=0
    )
    session.add(newsletter)
    session.commit()
    session.refresh(newsletter)
    
    response = client.delete(
        f"/newsletters/{newsletter.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    
    # Verify deleted
    response = client.get(
        f"/newsletters/{newsletter.id}",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    assert response.status_code == 404


def test_generate_newsletter_no_items(client, user_token):
    """Test generating newsletter with no items"""
    response = client.post(
        "/newsletters/generate",
        json={
            "item_ids": [],
            "subject_template": "Test Newsletter"
        },
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 400
    assert "선택된 아이템" in response.json()["detail"]


def test_health_check(client):
    """Test health endpoint"""
    response = client.get("/health")
    
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert "timestamp" in data
