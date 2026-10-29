<template>
  <div id="globalHeader">
    <a-row :wrap="false">
      <a-col flex="200px">
        <router-link to="/">
          <div class="title-bar">
            <img class="logo" src="E:\project\yu-picture\yu-picture-frontend\public\logo2.png" alt="logo" />
            <div class="title">像素仓云图库</div>
          </div>
        </router-link>
      </a-col>
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="current"
          mode="horizontal"
          :items="items"
          @click="doMenuClick"
        />
      </a-col>
      <!-- 用户信息展示栏 -->
      <a-col flex="140px">
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown
              v-model:open="userMenuOpen"
              :trigger="['click']"
              placement="bottomRight"
              overlay-class-name="user-dropdown"
            >
              <a-space :class="['user-trigger', { 'is-open': userMenuOpen }]">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
                <DownOutlined class="user-caret" />
              </a-space>
              <template #overlay>
                <a-menu @click="doUserMenuClick">
                  <a-menu-item>
                    <router-link to="/user/profile">
                      <IdcardOutlined />
                      我的页面
                    </router-link>
                  </a-menu-item>
                  <a-menu-item>
                    <router-link to="/my_space">
                      <UserOutlined />
                      我的空间
                    </router-link>
                  </a-menu-item>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>
<script lang="ts" setup>
import { computed, h, ref } from 'vue'
import { HomeOutlined, IdcardOutlined, DownOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'
import { MenuProps, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()

// 用户菜单展开状态
const userMenuOpen = ref(false)

// 点击菜单项后收起菜单
const doUserMenuClick = () => {
  userMenuOpen.value = false
}

// 未经过滤的菜单项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/add_picture',
    label: '创建图片',
    title: '创建图片',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/pictureManage',
    label: '图片管理',
    title: '图片管理',
  },
  {
    key: '/admin/spaceManage',
    label: '空间管理',
    title: '空间管理',
  },
  // {
  //   key: 'others',
  //   label: h('a', { href: 'https://www.codefather.cn', target: '_blank' }, '编程导航'),
  //   title: '编程导航',
  // },
]

// 根据权限过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    // 管理员才能看到 /admin 开头的菜单
    if (menu?.key?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const items = computed(() => filterMenus(originItems))

const router = useRouter()
// 当前要高亮的菜单项
const current = ref<string[]>([])
// 监听路由变化，更新高亮菜单项
router.afterEach((to, from, next) => {
  current.value = [to.path]
})

// 路由跳转事件
const doMenuClick = ({ key }) => {
  router.push({
    path: key,
  })
}

// 用户注销
const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#globalHeader .title-bar {
  display: flex;
  align-items: center;
}

.title {
  color: black;
  font-size: 18px;
  margin-left: 16px;
}

.logo {
  height: 48px;
}

#globalHeader .user-trigger {
  padding: 4px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

#globalHeader .user-trigger:hover,
#globalHeader .user-trigger.is-open {
  background: rgba(0, 0, 0, 0.04);
}

#globalHeader .user-caret {
  font-size: 10px;
  color: rgba(0, 0, 0, 0.45);
  transition: transform 0.2s;
}

#globalHeader .user-trigger.is-open .user-caret {
  transform: rotate(180deg);
}
</style>

<style>
.user-dropdown.ant-slide-up-enter.ant-slide-up-enter-active,
.user-dropdown.ant-slide-up-appear.ant-slide-up-appear-active {
  animation-name: userDropdownIn;
  animation-duration: 0.28s;
  animation-timing-function: cubic-bezier(0.22, 1, 0.36, 1);
}

.user-dropdown.ant-slide-up-leave.ant-slide-up-leave-active {
  animation-name: userDropdownOut;
  animation-duration: 0.16s;
}

@keyframes userDropdownIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes userDropdownOut {
  from {
    opacity: 1;
    transform: translateY(0);
  }
  to {
    opacity: 0;
    transform: translateY(-6px);
  }
}
</style>
