import pytest
from shared.auth import (
    hash_password, verify_password, generate_temp_code,
    is_account_locked, record_login_failure, reset_login_failures,
    create_token, parse_token
)
from shared.models import User


def test_password_hashing():
    """Test password hashing and verification"""
    password = "testpassword123"
    hashed = hash_password(password)
    
    assert hashed != password
    assert verify_password(password, hashed) is True
    assert verify_password("wrongpassword", hashed) is False


def test_generate_temp_code():
    """Test temporary code generation"""
    code = generate_temp_code()
    assert len(code) == 6
    assert code.isdigit()


def test_account_lockout(session, test_user):
    """Test account lockout after 5 failed attempts"""
    from datetime import datetime
    
    # Initially not locked
    assert is_account_locked(test_user) is False
    
    # Record 4 failures
    for _ in range(4):
        is_locked = record_login_failure(session, test_user)
        assert is_locked is False
    
    # 5th failure should lock
    is_locked = record_login_failure(session, test_user)
    assert is_locked is True
    assert is_account_locked(test_user) is True
    
    # Reset should unlock
    reset_login_failures(session, test_user)
    assert is_account_locked(test_user) is False


def test_token_creation_and_parsing(test_user):
    """Test token creation and parsing"""
    token = create_token(test_user)
    assert token == f"user:{test_user.email}"
    
    parsed = parse_token(token)
    assert parsed == test_user.email
    
    # Invalid tokens
    assert parse_token("invalid") is None
    assert parse_token(None) is None
    assert parse_token("") is None


def test_login_success(client, test_user):
    """Test successful login"""
    response = client.post("/auth/login", json={
        "email": test_user.email,
        "password": "testpassword123"
    })
    
    assert response.status_code == 200
    data = response.json()
    assert "token" in data
    assert data["role"] == "user"
    assert data["email"] == test_user.email


def test_login_wrong_password(client, test_user):
    """Test login with wrong password"""
    response = client.post("/auth/login", json={
        "email": test_user.email,
        "password": "wrongpassword"
    })
    
    assert response.status_code == 401


def test_login_nonexistent_user(client):
    """Test login with non-existent user"""
    response = client.post("/auth/login", json={
        "email": "nonexistent@example.com",
        "password": "somepassword"
    })
    
    assert response.status_code == 401


def test_login_account_lockout(client, test_user):
    """Test account lockout after multiple failures"""
    # Fail 5 times
    for _ in range(5):
        response = client.post("/auth/login", json={
            "email": test_user.email,
            "password": "wrongpassword"
        })
    
    # 6th attempt should show locked
    response = client.post("/auth/login", json={
        "email": test_user.email,
        "password": "testpassword123"
    })
    
    assert response.status_code == 403
    assert "잠겼" in response.json()["detail"]


def test_request_temp_password(client, test_user, monkeypatch):
    """Test temp password request"""
    sent_emails = []
    
    def mock_send(email, code):
        sent_emails.append((email, code))
        return {"success": True}
    
    # Mock the mailer
    from shared import mail
    original_send = mail.Mailer.send_temp_password
    mail.Mailer.send_temp_password = mock_send
    
    response = client.post("/auth/request-temp-password", json={
        "email": test_user.email
    })
    
    assert response.status_code == 200
    assert response.json()["ok"] is True
    assert len(sent_emails) == 1
    assert sent_emails[0][0] == test_user.email
    
    # Restore
    mail.Mailer.send_temp_password = original_send


def test_set_password(client, user_token):
    """Test setting new password"""
    response = client.post(
        "/auth/set-password",
        json={"new_password": "newpassword456"},
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200


def test_set_password_too_short(client, user_token):
    """Test setting password that's too short"""
    response = client.post(
        "/auth/set-password",
        json={"new_password": "short"},
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 400


def test_me_endpoint(client, user_token, test_user):
    """Test /me endpoint"""
    response = client.get(
        "/me",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["email"] == test_user.email
    assert data["role"] == "user"


def test_me_no_auth(client):
    """Test /me without auth"""
    response = client.get("/me")
    assert response.status_code == 401


def test_me_invalid_token(client):
    """Test /me with invalid token"""
    response = client.get(
        "/me",
        headers={"Authorization": "Bearer invalid-token"}
    )
    assert response.status_code == 401
