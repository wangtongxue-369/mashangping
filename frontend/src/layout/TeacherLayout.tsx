import { Layout, Menu } from 'antd';
import { AppstoreOutlined, BookOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const { Header, Content } = Layout;

const MENU_ITEMS = [
  { key: '/teacher/courses', icon: <AppstoreOutlined />, label: '课程' },
  { key: '/teacher/problems', icon: <BookOutlined />, label: '题库' },
];

/** 教师端主框架：白色顶栏（品牌 + 导航 + 用户名/退出）+ 浅灰内容区（与学生端观感一致）。 */
export default function TeacherLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // 子页（学生名单/作业/成绩册/查重）保持所属菜单高亮。
  const selectedKey = location.pathname.startsWith('/teacher/courses')
    ? '/teacher/courses'
    : location.pathname.startsWith('/teacher/problems')
      ? '/teacher/problems'
      : location.pathname;

  return (
    <Layout style={{ minHeight: '100vh', background: '#f4f5f7' }}>
      <Header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 20,
          background: '#fff',
          borderBottom: '1px solid #e6e8eb',
          display: 'flex',
          alignItems: 'center',
          gap: 28,
          paddingInline: 28,
          height: 56,
          lineHeight: '56px',
        }}
      >
        <div
          style={{
            color: '#1677ff',
            fontSize: 18,
            fontWeight: 700,
            cursor: 'pointer',
            whiteSpace: 'nowrap',
            userSelect: 'none',
          }}
          onClick={() => navigate('/teacher/courses')}
        >
          码上评
        </div>
        <Menu
          mode="horizontal"
          selectedKeys={[selectedKey]}
          items={MENU_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, minWidth: 0, borderBottom: 'none', fontWeight: 500 }}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <span style={{ color: '#1f2329', fontSize: 14 }}>{user?.realName || user?.username || ''}</span>
          <a onClick={logout} title="退出登录" style={{ color: '#6b7280' }}>
            <LogoutOutlined />
          </a>
        </div>
      </Header>
      <Content>
        <div className="msp-shell-content">
          <Outlet />
        </div>
      </Content>
    </Layout>
  );
}
