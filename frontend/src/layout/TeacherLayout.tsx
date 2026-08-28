import { Layout, Menu } from 'antd';
import { AppstoreOutlined, BookOutlined, LogoutOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const { Sider, Header, Content } = Layout;

const MENU_ITEMS = [
  { key: '/teacher/courses', icon: <AppstoreOutlined />, label: '课程' },
  { key: '/teacher/problems', icon: <BookOutlined />, label: '题库' },
];

/** 教师端主框架：Sider 菜单 + Header + 内容区 <Outlet/>。 */
export default function TeacherLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // 子页（学生名单/题目详情）也保持所属菜单高亮。
  const selectedKey = location.pathname.startsWith('/teacher/courses')
    ? '/teacher/courses'
    : location.pathname.startsWith('/teacher/problems')
      ? '/teacher/problems'
      : location.pathname;

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible theme="dark" width={200}>
        <div style={{ height: 32, margin: 16, color: '#fff', fontSize: 16, fontWeight: 600, textAlign: 'center' }}>
          码上评
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={MENU_ITEMS}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            gap: 16,
            paddingInline: 24,
          }}
        >
          <span>{user?.realName || user?.username || ''}</span>
          <a onClick={logout} title="退出登录">
            <LogoutOutlined />
          </a>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}