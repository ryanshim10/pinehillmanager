import pytest


def test_list_users_admin(client, admin_token, test_user, test_admin):
    """Test listing users as admin"""
    response = client.get(
        "/admin/users",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert "users" in data
    assert data["total"] >= 2
    
    emails = [u["email"] for u in data["users"]]
    assert test_user.email in emails
    assert test_admin.email in emails


def test_list_users_non_admin(client, user_token):
    """Test listing users as non-admin"""
    response = client.get(
        "/admin/users",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    
    assert response.status_code == 403


def test_create_user_admin(client, admin_token):
    """Test creating user as admin"""
    response = client.post(
        "/admin/users",
        json={"email": "newuser@example.com", "role": "user"},
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert "id" in data
    assert data["email"] == "newuser@example.com"


def test_create_user_duplicate_email(client, admin_token, test_user):
    """Test creating user with duplicate email"""
    response = client.post(
        "/admin/users",
        json={"email": test_user.email, "role": "user"},
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 400


def test_create_user_invalid_role(client, admin_token):
    """Test creating user with invalid role"""
    response = client.post(
        "/admin/users",
        json={"email": "test@example.com", "role": "invalid"},
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 400


def test_get_user_admin(client, admin_token, test_user):
    """Test getting user details as admin"""
    response = client.get(
        f"/admin/users/{test_user.id}",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["email"] == test_user.email
    assert data["role"] == test_user.role


def test_get_user_not_found(client, admin_token):
    """Test getting non-existent user"""
    response = client.get(
        "/admin/users/99999",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 404


def test_update_user_admin(client, admin_token, test_user):
    """Test updating user as admin"""
    response = client.patch(
        f"/admin/users/{test_user.id}",
        json={"enabled": False, "role": "admin"},
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 200
    
    # Verify update
    response = client.get(
        f"/admin/users/{test_user.id}",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    data = response.json()
    assert data["enabled"] is False
    assert data["role"] == "admin"


def test_unlock_user_admin(client, admin_token, test_user, session):
    """Test unlocking user as admin"""
    from datetime import datetime
    
    # Lock the user
    test_user.failed_login_count = 5
    test_user.locked_at = datetime.utcnow()
    session.add(test_user)
    session.commit()
    
    response = client.post(
        f"/admin/users/{test_user.id}/unlock",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 200
    
    # Verify unlocked
    session.refresh(test_user)
    assert test_user.locked_at is None
    assert test_user.failed_login_count == 0


def test_delete_user_admin(client, admin_token):
    """Test deleting user as admin"""
    # Create a user to delete
    response = client.post(
        "/admin/users",
        json={"email": "delete_me@example.com", "role": "user"},
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    user_id = response.json()["id"]
    
    # Delete
    response = client.delete(
        f"/admin/users/{user_id}",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 200
    
    # Verify deleted
    response = client.get(
        f"/admin/users/{user_id}",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert response.status_code == 404


def test_delete_self_fails(client, admin_token, test_admin):
    """Test that admin cannot delete themselves"""
    response = client.delete(
        f"/admin/users/{test_admin.id}",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    
    assert response.status_code == 400


def test_admin_endpoints_require_auth(client):
    """Test that admin endpoints require authentication"""
    endpoints = [
        ("get", "/admin/users"),
        ("post", "/admin/users"),
        ("get", "/admin/users/1"),
        ("patch", "/admin/users/1"),
        ("delete", "/admin/users/1"),
    ]
    
    for method, endpoint in endpoints:
        response = getattr(client, method)(endpoint)
        assert response.status_code == 401, f"{method.upper()} {endpoint} should require auth"
