import os
import requests
import streamlit as st

API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8001")

st.set_page_config(page_title="AI Playground", layout="wide")
st.title("AI Playground")

st.caption("사번/로그인ID는 실제 운영에서는 프록시가 SSO 헤더로 주입합니다. (여긴 테스트용)")

col1, col2 = st.columns(2)
with col1:
    empid = st.text_input("X-SSO-EMPID (사번)")
with col2:
    loginid = st.text_input("X-SSO-LOGINID (로그인ID)")

prompt = st.text_area("Prompt", height=180)

if st.button("Run", type="primary"):
    headers = {}
    if empid:
        headers["X-SSO-EMPID"] = empid
    if loginid:
        headers["X-SSO-LOGINID"] = loginid

    r = requests.post(f"{API_BASE_URL}/run", json={"prompt": prompt}, headers=headers, timeout=30)
    st.write("Status:", r.status_code)
    st.json(r.json())
