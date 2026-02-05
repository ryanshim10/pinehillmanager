import os
import io
import requests
import streamlit as st

API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8001")

st.set_page_config(page_title="AI Playground", layout="wide")

# Initialize session state
if "token" not in st.session_state:
    st.session_state.token = ""
if "user_info" not in st.session_state:
    st.session_state.user_info = None
if "current_app" not in st.session_state:
    st.session_state.current_app = None

def get_headers():
    """Get auth headers."""
    headers = {}
    if st.session_state.token:
        headers["Authorization"] = f"Bearer {st.session_state.token}"
    return headers

def login_dev(email, password):
    """Login in dev mode."""
    r = requests.post(
        f"{API_BASE_URL}/auth/login",
        json={"email": email, "password": password},
        timeout=30
    )
    if r.status_code == 200:
        data = r.json()
        st.session_state.token = data["token"]
        return True
    return False

def get_me():
    """Get current user info."""
    r = requests.get(f"{API_BASE_URL}/me", headers=get_headers(), timeout=30)
    if r.status_code == 200:
        st.session_state.user_info = r.json()
        return True
    return False

def list_apps():
    """List accessible apps."""
    r = requests.get(f"{API_BASE_URL}/apps", headers=get_headers(), timeout=30)
    if r.status_code == 200:
        return r.json()
    return []

def create_app(name, description, is_shared):
    """Create a new app."""
    r = requests.post(
        f"{API_BASE_URL}/apps",
        json={"name": name, "description": description, "is_shared": is_shared},
        headers=get_headers(),
        timeout=30
    )
    if r.status_code == 200:
        return r.json()
    return None

def upload_app(app_id, file):
    """Upload app files."""
    files = {"file": (file.name, file.getvalue(), "application/zip")}
    r = requests.post(
        f"{API_BASE_URL}/apps/{app_id}/upload",
        files=files,
        headers=get_headers(),
        timeout=60
    )
    return r.status_code == 200, r.json() if r.status_code == 200 else r.text

def run_app(app_id):
    """Start running an app."""
    r = requests.post(
        f"{API_BASE_URL}/apps/{app_id}/run",
        headers=get_headers(),
        timeout=30
    )
    if r.status_code == 200:
        return r.json()
    return None

def stop_app(app_id):
    """Stop a running app."""
    r = requests.post(
        f"{API_BASE_URL}/apps/{app_id}/stop",
        headers=get_headers(),
        timeout=30
    )
    return r.status_code == 200

def get_app_status(app_id):
    """Get app execution status."""
    r = requests.get(
        f"{API_BASE_URL}/apps/{app_id}/status",
        headers=get_headers(),
        timeout=30
    )
    if r.status_code == 200:
        return r.json()
    return None

def list_collaborators(app_id):
    """List app collaborators."""
    r = requests.get(
        f"{API_BASE_URL}/apps/{app_id}/collaborators",
        headers=get_headers(),
        timeout=30
    )
    if r.status_code == 200:
        return r.json()
    return []

def add_collaborator(app_id, user_id, role):
    """Add a collaborator."""
    r = requests.post(
        f"{API_BASE_URL}/apps/{app_id}/collaborators",
        json={"user_id": user_id, "role": role},
        headers=get_headers(),
        timeout=30
    )
    return r.status_code == 200

def remove_collaborator(app_id, user_id):
    """Remove a collaborator."""
    r = requests.delete(
        f"{API_BASE_URL}/apps/{app_id}/collaborators/{user_id}",
        headers=get_headers(),
        timeout=30
    )
    return r.status_code == 200

def delete_app(app_id):
    """Delete an app."""
    r = requests.delete(
        f"{API_BASE_URL}/apps/{app_id}",
        headers=get_headers(),
        timeout=30
    )
    return r.status_code == 200

# -------------------- UI --------------------

st.title("🚀 AI Playground")
st.caption("Streamlit 앱 업로드, 공유, 실행 플랫폼")

# Sidebar for auth
with st.sidebar:
    st.header("인증")
    
    mode = st.selectbox("AUTH_MODE", options=["dev", "sso"], index=0)
    
    if mode == "dev":
        if not st.session_state.token:
            email = st.text_input("Email", value="dev@local")
            password = st.text_input("Password", value="devpass", type="password")
            if st.button("Login"):
                if login_dev(email, password):
                    get_me()
                    st.rerun()
                else:
                    st.error("Login failed")
        else:
            if st.button("Logout"):
                st.session_state.token = ""
                st.session_state.user_info = None
                st.rerun()
    else:
        st.caption("SSO 모드: 헤더 입력")
        empid = st.text_input("X-SSO-EMPID (사번)")
        loginid = st.text_input("X-SSO-LOGINID (로그인ID)")
        if st.button("Apply SSO Headers"):
            st.session_state.user_info = {
                "empid": empid,
                "loginid": loginid
            }
            st.rerun()
    
    if st.session_state.user_info:
        st.success(f"Logged in as: {st.session_state.user_info.get('loginid') or st.session_state.user_info.get('email')}")

# Main content
if not st.session_state.token and mode == "dev":
    st.info("👈 사이드바에서 로그인해주세요")
elif not st.session_state.user_info and mode == "sso":
    st.info("👈 SSO 헤더를 입력해주세요")
else:
    # Tabs for different features
    tab_apps, tab_create, tab_manage = st.tabs(["📱 내 앱", "➕ 새 앱 만들기", "⚙️ 관리"])
    
    # Tab: My Apps
    with tab_apps:
        st.subheader("내 앱 목록")
        
        apps = list_apps()
        if not apps:
            st.info("아직 앱이 없습니다. '새 앱 만들기' 탭에서 생성하세요!")
        else:
            for app in apps:
                with st.container():
                    col1, col2, col3, col4 = st.columns([3, 2, 2, 2])
                    
                    with col1:
                        st.write(f"**{app['name']}**")
                        st.caption(f"{app['description'] or '설명 없음'}")
                    
                    with col2:
                        status_color = "🟢" if app['status'] == 'active' else "🟡" if app['status'] == 'draft' else "⚪"
                        st.write(f"{status_color} {app['status']}")
                        if app['is_shared']:
                            st.caption("🌐 조직 공유")
                    
                    with col3:
                        if app['status'] == 'active':
                            if st.button("▶️ 실행", key=f"run_{app['id']}"):
                                with st.spinner("앱 시작 중..."):
                                    result = run_app(app['id'])
                                    if result:
                                        st.session_state[f"run_url_{app['id']}"] = result.get('url')
                                        st.success(f"앱 실행 중! 포트: {result.get('port')}")
                                    else:
                                        st.error("실행 실패")
                        
                        if st.button("⏹️ 중지", key=f"stop_{app['id']}"):
                            if stop_app(app['id']):
                                st.success("앱 중지됨")
                                if f"run_url_{app['id']}" in st.session_state:
                                    del st.session_state[f"run_url_{app['id']}"]
                            else:
                                st.error("중지 실패")
                    
                    with col4:
                        if app.get('can_manage'):
                            if st.button("⚙️ 관리", key=f"manage_{app['id']}"):
                                st.session_state.current_app = app
                                st.rerun()
                    
                    # Show run URL if available
                    run_url_key = f"run_url_{app['id']}"
                    if run_url_key in st.session_state:
                        url = st.session_state[run_url_key]
                        st.success(f"🌐 [앱 열기]({url})")
                    
                    st.divider()
    
    # Tab: Create App
    with tab_create:
        st.subheader("새 앱 만들기")
        
        app_name = st.text_input("앱 이름", placeholder="my-awesome-app")
        app_desc = st.text_area("설명 (선택)", placeholder="이 앱은...")
        is_shared = st.checkbox("조직 내에서 공유", value=False)
        
        if st.button("앱 생성", type="primary"):
            if app_name:
                with st.spinner("생성 중..."):
                    new_app = create_app(app_name, app_desc, is_shared)
                    if new_app:
                        st.success(f"앱 생성 완료! ID: {new_app['id']}")
                    else:
                        st.error("생성 실패")
            else:
                st.error("앱 이름을 입력하세요")
        
        st.divider()
        st.subheader("앱 템플릿")
        st.code('''import streamlit as st

st.title("My Streamlit App")
st.write("Hello from AI Playground!")

# Add your code here
name = st.text_input("Your name")
if name:
    st.write(f"Hello, {name}!")
''', language='python')
    
    # Tab: App Management
    with tab_manage:
        if not st.session_state.current_app:
            st.info("관리할 앱을 선택하세요 (내 앱 탭에서 '관리' 버튼 클릭)")
            
            # List apps with manage option
            apps = list_apps()
            manage_apps = [a for a in apps if a.get('can_manage')]
            if manage_apps:
                st.subheader("관리 가능한 앱")
                for app in manage_apps:
                    if st.button(f"⚙️ {app['name']}", key=f"select_{app['id']}"):
                        st.session_state.current_app = app
                        st.rerun()
        else:
            app = st.session_state.current_app
            
            st.subheader(f"관리: {app['name']}")
            
            col1, col2 = st.columns([1, 1])
            with col1:
                if st.button("← 돌아가기"):
                    st.session_state.current_app = None
                    st.rerun()
            with col2:
                if st.button("🗑️ 앱 삭제", type="secondary"):
                    if delete_app(app['id']):
                        st.success("앱 삭제됨")
                        st.session_state.current_app = None
                        st.rerun()
                    else:
                        st.error("삭제 실패")
            
            # Upload section
            st.divider()
            st.subheader("📦 파일 업로드")
            st.caption("ZIP 파일 형식으로 업로드 (app.py 또는 main.py 필수)")
            
            uploaded_file = st.file_uploader("앱 ZIP 파일", type=['zip'])
            if uploaded_file:
                if st.button("업로드", type="primary"):
                    with st.spinner("업로드 중..."):
                        success, result = upload_app(app['id'], uploaded_file)
                        if success:
                            st.success("업로드 완료!")
                            st.json(result)
                        else:
                            st.error(f"업로드 실패: {result}")
            
            # Collaborators section
            st.divider()
            st.subheader("👥 협업자 관리")
            
            collabs = list_collaborators(app['id'])
            
            if collabs:
                st.write("현재 협업자:")
                for c in collabs:
                    col1, col2, col3 = st.columns([3, 2, 1])
                    with col1:
                        st.write(c['user_id'])
                    with col2:
                        st.caption(f"역할: {c['role']}")
                    with col3:
                        if st.button("❌", key=f"remove_{c['id']}"):
                            if remove_collaborator(app['id'], c['user_id']):
                                st.success("제거됨")
                                st.rerun()
                            else:
                                st.error("실패")
            else:
                st.caption("협업자 없음")
            
            with st.expander("협업자 추가"):
                new_user = st.text_input("사용자 ID (email 또는 loginid)")
                new_role = st.selectbox("역할", ["viewer", "collaborator", "admin"])
                if st.button("추가"):
                    if new_user:
                        if add_collaborator(app['id'], new_user, new_role):
                            st.success("추가됨")
                            st.rerun()
                        else:
                            st.error("추가 실패")
    
    # Legacy run section (for backward compatibility)
    st.divider()
    with st.expander("🧪 레거시 프롬프트 실행"):
        prompt = st.text_area("Prompt", height=100)
        if st.button("Run (Legacy)", type="secondary"):
            r = requests.post(
                f"{API_BASE_URL}/run",
                json={"prompt": prompt},
                headers=get_headers(),
                timeout=30
            )
            st.write("Status:", r.status_code)
            try:
                st.json(r.json())
            except Exception:
                st.write(r.text)
