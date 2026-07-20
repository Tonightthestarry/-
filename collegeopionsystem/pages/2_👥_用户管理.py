"""页面2：用户管理 - 管理员 CRUD、角色管理、密码修改"""
import streamlit as st
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from backend.db_mysql import (
    get_all_users, add_user, update_user_role,
    update_user_password, delete_user, verify_user
)

st.set_page_config(page_title="用户管理", page_icon="👥", layout="wide")

st.title("👥 用户管理")
st.markdown("---")

# Session state 保存当前登录用户
if 'current_user' not in st.session_state:
    st.session_state.current_user = None
if 'is_admin' not in st.session_state:
    st.session_state.is_admin = False

# ========== 登录区域（右上角） ==========
login_col1, login_col2, login_col3, login_col4 = st.columns([2, 2, 1, 1])

if st.session_state.current_user is None:
    with login_col1:
        login_username = st.text_input("用户名", placeholder="admin", key="login_user")
    with login_col2:
        login_password = st.text_input("密码", type="password", placeholder="admin123", key="login_pass")
    with login_col3:
        st.markdown("<br>", unsafe_allow_html=True)
        if st.button("🔑 登录", use_container_width=True):
            user = verify_user(login_username, login_password)
            if user:
                st.session_state.current_user = user
                st.session_state.is_admin = (user['role'] == 'admin')
                st.success(f"✅ 登录成功! 欢迎 {user['username']}")
                st.rerun()
            else:
                st.error("用户名或密码错误!")
else:
    with login_col1:
        st.info(f"当前用户: **{st.session_state.current_user['username']}** ({st.session_state.current_user['role']})")
    with login_col4:
        st.markdown("<br>", unsafe_allow_html=True)
        if st.button("退出", use_container_width=True):
            st.session_state.current_user = None
            st.session_state.is_admin = False
            st.rerun()

st.markdown("---")

if not st.session_state.current_user:
    st.warning("请先登录")
    st.info("默认管理员: admin / admin123")
    st.info("默认普通用户: user1 / 123456")
    st.stop()

# ========== 管理员功能 ==========
if st.session_state.is_admin:
    tab1, tab2, tab3 = st.tabs(["📋 用户列表", "➕ 添加用户", "🔄 管理用户"])

    with tab1:
        st.subheader("所有用户")
        try:
            users = get_all_users()
            if users:
                import pandas as pd
                df = pd.DataFrame(users)
                df.columns = ['ID', '用户名', '角色', '创建时间']
                st.dataframe(df, use_container_width=True, hide_index=True)
                st.caption(f"共 {len(users)} 位用户")
            else:
                st.info("暂无用户")
        except Exception as e:
            st.error(f"加载用户列表失败: {e}")

    with tab2:
        st.subheader("添加新用户")
        new_username = st.text_input("新用户名", placeholder="至少3个字符", key="new_user")
        new_password = st.text_input("新密码", type="password", placeholder="至少4个字符", key="new_pass")
        new_role = st.selectbox("角色", ["user", "admin"], key="new_role")

        if st.button("✅ 确认添加", use_container_width=True):
            if len(new_username) < 3:
                st.error("用户名至少3个字符")
            elif len(new_password) < 4:
                st.error("密码至少4个字符")
            else:
                try:
                    add_user(new_username, new_password, new_role)
                    st.success(f"✅ 用户 {new_username} 添加成功!")
                    st.rerun()
                except Exception as e:
                    st.error(f"添加失败: {e}")

    with tab3:
        st.subheader("管理用户")
        try:
            users = get_all_users()
            if users:
                for u in users:
                    with st.expander(f"{u['username']} ({u['role']})"):
                        col1, col2, col3 = st.columns(3)
                        with col1:
                            new_role = st.selectbox(
                                "角色", ["user", "admin"],
                                index=0 if u['role'] == 'user' else 1,
                                key=f"role_{u['id']}"
                            )
                            if st.button("修改角色", key=f"role_btn_{u['id']}"):
                                update_user_role(u['id'], new_role)
                                st.success("修改成功!")
                                st.rerun()
                        with col2:
                            new_pwd = st.text_input("新密码", type="password", key=f"pwd_{u['id']}")
                            if st.button("修改密码", key=f"pwd_btn_{u['id']}"):
                                if len(new_pwd) < 4:
                                    st.error("密码至少4位")
                                else:
                                    update_user_password(u['id'], new_pwd)
                                    st.success("修改成功!")
                        with col3:
                            st.markdown("<br>", unsafe_allow_html=True)
                            if u['role'] != 'admin' and st.button("删除用户", key=f"del_{u['id']}", type="secondary"):
                                delete_user(u['id'])
                                st.success("删除成功!")
                                st.rerun()
        except Exception as e:
            st.error(f"加载失败: {e}")

else:
    # 普通用户功能
    st.subheader("🔒 个人设置")
    st.info(f"当前角色: **普通用户** (仅可修改自己的密码)")

    new_pwd = st.text_input("输入新密码", type="password", key="self_pwd")
    if st.button("修改我的密码", use_container_width=True):
        if len(new_pwd) < 4:
            st.error("密码至少4个字符")
        else:
            try:
                update_user_password(st.session_state.current_user['id'], new_pwd)
                st.success("✅ 密码修改成功!")
            except Exception as e:
                st.error(f"修改失败: {e}")
