export default {
  '/api': {
    // 后端 Admin 端口为 7070；若后端端口变更，请同步修改此处
    target: 'http://localhost:7070',
    changeOrigin: true,
  },
};
