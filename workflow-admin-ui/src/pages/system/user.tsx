import React, { useState, useCallback } from 'react';
import {
  Card, Table, Button, Input, Space, Tag, Modal, Form, Select, message, Popconfirm,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { listUsers, createUser, updateUser, deleteUser, type User, type UserRequest } from '@/services/user';
import { listRoles } from '@/services/role';
import { useClickDebounce } from '@/utils/useClickDebounce';

const UserPage: React.FC = () => {
  const [keyword, setKeyword] = useState('');
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [form] = Form.useForm<UserRequest>();

  const { data, loading, run } = useRequest(
    async () => {
      const res = await listUsers({
        keyword: keyword || undefined,
        page: pagination.current - 1,
        pageSize: pagination.pageSize,
      });
      return { list: res?.list ?? [], total: res?.total ?? 0 };
    },
    { refreshDeps: [keyword, pagination] },
  );

  const { data: roleData } = useRequest(async () => {
    const res = await listRoles({ page: 0, pageSize: 100 });
    return res?.list ?? [];
  });

  const handleSearch = useClickDebounce(() => { run(); });

  const openCreate = useCallback(() => {
    setEditingUser(null);
    form.resetFields();
    form.setFieldsValue({ status: 'ACTIVE' });
    setModalOpen(true);
  }, [form]);

  const openEdit = useCallback((record: User) => {
    setEditingUser(record);
    form.setFieldsValue({
      username: record.username,
      nickname: record.nickname,
      email: record.email,
      phone: record.phone,
      status: record.status,
      roleIds: record.roles,
    });
    setModalOpen(true);
  }, [form]);

  const handleSubmit = useClickDebounce(async () => {
    const values = await form.validateFields();
    if (editingUser?.id) {
      await updateUser(editingUser.id, values);
      message.success('更新成功');
    } else {
      await createUser(values);
      message.success('创建成功');
    }
    setModalOpen(false);
    run();
  });

  const handleDelete = useClickDebounce(async (id: string) => {
    await deleteUser(id);
    message.success('删除成功');
    run();
  });

  const columns: ColumnsType<User> = [
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '昵称', dataIndex: 'nickname', width: 120 },
    { title: '邮箱', dataIndex: 'email', ellipsis: true },
    { title: '手机', dataIndex: 'phone', width: 130 },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : 'red'}>{v === 'ACTIVE' ? '启用' : '禁用'}</Tag>,
    },
    {
      title: '角色', dataIndex: 'roles', width: 180,
      render: (roles?: string[]) => roles?.map((r) => <Tag key={r} color="#6366f1">{r}</Tag>),
    },
    {
      title: '创建时间', dataIndex: 'createdAt', width: 170,
      render: (v?: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: '操作', width: 140, fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除该用户？" onConfirm={() => handleDelete(record.id!)} okText="确定" cancelText="取消">
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="用户管理"
      extra={
        <Space>
          <Input
            placeholder="用户名/昵称"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={handleSearch}
            style={{ width: 160 }}
            allowClear
          />
          <Button icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增用户</Button>
        </Space>
      }
    >
      <Table<User>
        rowKey="id"
        dataSource={data?.list ?? []}
        columns={columns}
        loading={loading}
        scroll={{ x: 1000 }}
        size="small"
        onChange={(pag: TablePaginationConfig) => setPagination({ current: pag.current ?? 1, pageSize: pag.pageSize ?? 20 })}
        pagination={{ current: pagination.current, pageSize: pagination.pageSize, total: data?.total ?? 0, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />
      <Modal title={editingUser ? '编辑用户' : '新增用户'} open={modalOpen} onOk={handleSubmit} onCancel={() => setModalOpen(false)} destroyOnClose width={500}>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]} style={{ marginBottom: 12 }}>
            <Input disabled={!!editingUser} />
          </Form.Item>
          <Form.Item name="nickname" label="昵称" style={{ marginBottom: 12 }}><Input /></Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]} style={{ marginBottom: 12 }}><Input /></Form.Item>
          <Form.Item name="phone" label="手机" style={{ marginBottom: 12 }}><Input /></Form.Item>
          <Form.Item name="status" label="状态" style={{ marginBottom: 12 }}>
            <Select options={[{ label: '启用', value: 'ACTIVE' }, { label: '禁用', value: 'INACTIVE' }]} />
          </Form.Item>
          <Form.Item name="roleIds" label="角色" style={{ marginBottom: 12 }}>
            <Select mode="multiple" placeholder="选择角色" options={(roleData ?? []).map((r) => ({ label: r.name, value: r.id ?? r.code }))} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default UserPage;
