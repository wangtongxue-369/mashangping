import { Layout, Menu } from 'antd';
import { BookOutlined, LogoutOutlined, ShoppingOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const { Header, Content } = Layout;

const MENU_ITEMS = [
  { key: '/student/courses', icon: <ShoppingOutlined />, label: '我的课程' },
  { key: '/student/practice/problems', icon: <BookOutlined />, label: '自由练习' },
];

/** 学生端主框架：白色顶栏（品牌 + 导航 + 用户名/退出）+ 浅灰内容区。 */
export default function StudentLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // 子页（课程作业/题目/编码）也保持所属导航高亮。
  const selectedKey = location.pathname.startsWith('/student/courses')
    ? '/student/courses'
    : location.pathname.startsWith('/student/practice')
      ? '/student/practice/problems'
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
          onClick={() => navigate('/student/courses')}
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
