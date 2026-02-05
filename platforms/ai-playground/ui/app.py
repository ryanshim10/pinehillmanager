import os
import requests
import streamlit as st

API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8001")

st.set_page_config(page_title="AI Playground", layout="wide")
st.title("AI Playground")

mode = st.selectbox("AUTH_MODE", options=["dev", "sso"], index=0)

if "token" not in st.session_state:
    st.session_state.token = ""

if mode == "dev":
    st.caption("DEV 모드: email/password 로그인 후 토큰으로 실행")
    email = st.text_input("Email", value="dev@local")
    password = st.text_input("Password", value="devpass", type="password")
    if st.button("Login", type="secondary"):
        r = requests.post(f"{API_BASE_URL}/auth/login", json={"email": email, "password": password}, timeout=30)
        if r.status_code == 200:
            st.session_state.token = r.json()["token"]
            st.success("Logged in")
        else:
            st.error(r.text)
else:
    st.caption("SSO 모드: 프록시가 SSO 헤더를 주입하는 것을 가정(테스트용 입력)")
    col1, col2 = st.columns(2)
    with col1:
        empid = st.text_input("X-SSO-EMPID (사번)")
    with col2:
        loginid = st.text_input("X-SSO-LOGINID (로그인ID)")

prompt = st.text_area("Prompt", height=180)

if st.button("Run", type="primary"):
    headers = {}
    if mode == "dev":
        if st.session_state.token:
            headers["Authorization"] = f"Bearer {st.session_state.token}"
    else:
        if empid:
            headers["X-SSO-EMPID"] = empid
        if loginid:
            headers["X-SSO-LOGINID"] = loginid

    r = requests.post(f"{API_BASE_URL}/run", json={"prompt": prompt}, headers=headers, timeout=30)
    st.write("Status:", r.status_code)
    try:
        st.json(r.json())
    except Exception:
        st.write(r.text)
