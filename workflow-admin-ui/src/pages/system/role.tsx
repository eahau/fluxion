import React, { useState, useCallback } from 'react';
import {
  Card, Table, Button, Input, Space, Tag, Modal, Form, Checkbox, message, Popconfirm,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useRequest } from '@umijs/max';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { listRoles, createRole, updateRole, deleteRole, type Role, type RoleRequest } from '@/services/role';
import { useClickDebounce } from '@/utils/useClickDebounce';

const ALL_PERMISSIONS = [
  { label: '查看 Schema', value: 'schema:view' },
  { label: '编辑 Schema', value: 'schema:edit' },
  { label: '发布 Schema', value: 'schema:publish' },
  { label: '查看工作流', value: 'workflow:view' },
  { label: '编辑工作流', value: 'workflow:edit' },
  { label: '发布工作流', value: 'workflow:publish' },
  { label: '执行工作流', value: 'workflow:execute' },
  { label: '查看函数', value: 'function:view' },
  { label: '编辑函数', value: 'function:edit' },
  { label: '查看监控', value: 'monitor:view' },
  { label: '管理用户', value: 'user:manage' },
  { label: '管理角色', value: 'role:manage' },
  { label: '审计日志', value: 'audit:view' },
  { label: '系统管理', value: 'system:admin' },
];

const RolePage: React.FC = () => {
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20 });
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [form] = Form.useForm<RoleRequest>();

  const { data, loading, run } = useRequest(
    async () => {
      const res = await listRoles({ page: pagination.current - 1, pageSize: pagination.pageSize });
      return { list: res?.list ?? [], total: res?.total ?? 0 };
    },
    { refreshDeps: [pagination] },
  );

  const openCreate = useCallback(() => {
    setEditingRole(null);
    form.resetFields();
    form.setFieldsValue({ permissions: [] });
    setModalOpen(true);
  }, [form]);

  const openEdit = useCallback((record: Role) => {
    setEditingRole(record);
    form.setFieldsValue({ name: record.name, code: record.code, description: record.description, permissions: record.permissions });
    setModalOpen(true);
  }, [form]);

  const handleSubmit = useClickDebounce(async () => {
    const values = await form.validateFields();
    if (editingRole?.id) {
      await updateRole(editingRole.id, values);
      message.success('更新成功');
    } else {
      await createRole(values);
      message.success('创建成功');
    }
    setModalOpen(false);
    run();
  });

  const handleDelete = useClickDebounce(async (id: string) => {
    await deleteRole(id);
    message.success('删除成功');
    run();
  });

  const columns: ColumnsType<Role> = [
    { title: '角色名称', dataIndex: 'name', width: 150 },
    { title: '角色编码', dataIndex: 'code', width: 150 },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '权限', dataIndex: 'permissions', width: 360,
      render: (perms?: string[]) => perms?.map((p) => {
        const found = ALL_PERMISSIONS.find((x) => x.value === p);
        return <Tag key={p} color="#6366f1">{found?.label ?? p}</Tag>;
      }),
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
          <Popconfirm title="确定删除该角色？" onConfirm={() => handleDelete(record.id!)} okText="确定" cancelText="取消">
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title="角色管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增角色</Button>}>
      <Table<Role>
        rowKey="id"
        dataSource={data?.list ?? []}
        columns={columns}
        loading={loading}
        scroll={{ x: 900 }}
        size="small"
        onChange={(pag: TablePaginationConfig) => setPagination({ current: pag.current ?? 1, pageSize: pag.pageSize ?? 20 })}
        pagination={{ current: pagination.current, pageSize: pagination.pageSize, total: data?.total ?? 0, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />
      <Modal title={editingRole ? '编辑角色' : '新增角色'} open={modalOpen} onOk={handleSubmit} onCancel={() => setModalOpen(false)} destroyOnClose width={560}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]} style={{ marginBottom: 12 }}><Input /></Form.Item>
          <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]} style={{ marginBottom: 12 }}>
            <Input disabled={!!editingRole} placeholder="如 ADMIN / EDITOR / VIEWER" />
          </Form.Item>
          <Form.Item name="description" label="描述" style={{ marginBottom: 12 }}><Input.TextArea rows={2} /></Form.Item>
          <Form.Item name="permissions" label="权限" style={{ marginBottom: 12 }}>
            <Checkbox.Group>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '4px 0' }}>
                {ALL_PERMISSIONS.map((p) => <Checkbox key={p.value} value={p.value}>{p.label}</Checkbox>)}
              </div>
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default RolePage;
