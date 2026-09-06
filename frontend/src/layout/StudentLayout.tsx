import { Layout, Menu } from 'antd';
import { BookOutlined, LogoutOutlined, ShoppingOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const { Header, Content } = Layout;

const MENU_ITEMS = [
  { key: '/student/courses', icon: <ShoppingOutlined />, label: '我的课程' },
  { key: '/student/practice/problems', icon: <BookOutlined />, label: '自由练习' },
];

/** 学生端主框架：顶栏（菜单 + 用户名/退出）+ 内容区 <Outlet/>。 */
export default function StudentLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // 子页（课程作业/题目/编码）也保持所属菜单高亮。
  const selectedKey = location.pathname.startsWith('/student/courses')
    ? '/student/courses'
    : location.pathname.startsWith('/student/practice')
      ? '/student/practice/problems'
      : location.pathname;

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          background: '#001529',
          display: 'flex',
          alignItems: 'center',
          gap: 24,
          paddingInline: 24,
        }}
      >
        <div style={{ color: '#fff', fontSize: 16, fontWeight: 600 }}>码上评</div>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[selectedKey]}
          items={MENU_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, minWidth: 0 }}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span style={{ color: '#fff' }}>{user?.realName || user?.username || ''}</span>
          <a onClick={logout} title="退出登录" style={{ color: '#fff' }}>
            <LogoutOutlined />
          </a>
        </div>
      </Header>
      <Content style={{ margin: 16 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
