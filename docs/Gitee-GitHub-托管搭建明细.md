# Gitee / GitHub 托管搭建实施明细

> 项目：老人提醒软件
> 日期：2026-10-31
> 环境结论：沙盒内 git 2.43 / gh 2.45 / ssh 可用；当前无 SSH 密钥、未配置 git 身份

本明细将「建立 Gitee / GitHub 托管」拆分为可操作步骤，标注执行人：
- **🔵 你操作**：必须使用你的账号、身份或浏览器，我无法代办
- **🟢 我可代劳**：在沙盒内即可执行，但可能需要你提供凭据

---

## 第 0 步：前置条件（你操作）

| 项 | 内容 | 说明 |
|----|------|------|
| 1 | 注册/登录 **Gitee** 账号 | gitee.com，需实名认证（国内） |
| 2 | 注册/登录 **GitHub** 账号 | github.com |
| 3 | 准备身份信息 | 你用于提交代码的 `user.name` 和 `user.email`（建议用 GitHub 的 noreply 邮箱隐藏真实邮箱） |

---

## 第 1 步：生成 SSH 密钥（我代劳 + 你确认）

- **🟢 我代劳**：在沙盒内执行 `ssh-keygen` 生成一对 Ed25519 密钥
- **🔵 你操作**：将公钥分别粘贴到 Gitee / GitHub 的 SSH Keys 设置页
- 说明：SSH 用于 `git push/pull`，比 HTTPS 免去反复输密码

### 密钥生成命令（我执行）
```bash
ssh-keygen -t ed25519 -C "老人提醒软件" -f ~/.ssh/id_ed25519 -N ""
```

### 你粘贴公钥的位置
- **GitHub**：Settings → SSH and GPG keys → New SSH key
- **Gitee**：个人主页 → 设置 → 安全设置 → SSH 公钥

---

## 第 2 步：创建远程仓库（你操作）

### GitHub 仓库（我用 gh 可代办，需你授权）
- **推荐**：我用 `gh auth login` 引导你完成浏览器授权（一次性）
- 之后我用 `gh repo create` 帮你建仓库
- 仓库名建议：`elderly-reminder-app`
- **已确定：`Public` 公开**（✅ 2026-10-31 用户确认）。原因：APK 分发最省事，下载链接发给对方无需登录即可安装；源码公开对个人小项目无损失。

### Gitee 仓库（你操作）
- Gitee 在网页端建仓更稳妥（`gh` 不支持 Gitee）
- 你登录 gitee.com → 右上角 `+` → 新建仓库
- 仓库名：`elderly-reminder-app`
- 建议 `公开`，勾选（可选）初始化 README

> 说明：如果你希望两个平台都建，建议 **GitHub 为主仓（source of truth）**，**Gitee 作为镜像/分发仓**，这样一套源码双向同步。

---

## 第 3 步：配置 git 身份（我代劳，需你提供值）

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱或noreply"
```

需要你提供：`user.name` 与 `user.email` 的具体值。

---

## 第 4 步：本地初始化与首次推送（我代劳）

在 `/workspace/老人提醒软件` 下：
```bash
git init
git add .
git commit -m "初始化：老人提醒软件项目空间"
git branch -M main
git remote add origin <GitHub仓库地址>   # 或 gitee
git push -u origin main
```

> 说明：当前 `/workspace/老人提醒软件` 是任务空间根目录，也是源码仓。APK 产物可放入 `apk/` 并加入 `.gitignore`，或单独用 Release 附件分发（推荐）。

---

## 第 5 步：配置 .gitignore（我代劳）

需忽略：本地构建产物、APK 大文件（用 Release 附件替代入库）、IDE 配置等。示例：
```gitignore
# 构建产物
app/build/
build/
*.apk
*.aab
# 本地环境
local.properties
.idea/
*.iml
.DS_Store
```

---

## 第 6 步：APK 上传与分发（混合）

### 分发载体：GitHub/Gitee Release
- **Release 附件（推荐）**：每个版本打一个 Release，上传 `*.apk` 作为附件
  - 用 `gh release create`（GitHub，我可代劳）
  - Gitee 需网页上传（你操作）
- 下载地址格式：
  - GitHub：`https://github.com/<你的名>/elderly-reminder-app/releases`
  - Gitee：`https://gitee.com/<你的名>/elderly-reminder-app/releases`

### 可选：CI 自动构建
- GitHub Actions：`.github/workflows/build.yml` 打 APK 并自动挂到 Release（我代劳）
- Gitee Go：需在 Gitee 网页开启（你操作）

---

## 第 7 步：验证（混合）

- [ ] `git remote -v` 显示两个远程地址（我代劳）
- [ ] `git push` 无报错（我代劳）
- [ ] 网页打开仓库能看到 README（你确认）
- [ ] 网页打开 Release 能下载 APK（你确认）

---

## 责任矩阵速览

| 步骤 | 内容 | 执行人 |
|------|------|--------|
| 0 | 注册账号 / 准备身份 | 🔵 你 |
| 1 | 生成 SSH 密钥 | 🟢 我（密钥）+ 🔵 你（粘贴公钥） |
| 2 | 创建 GitHub 仓库 | 🟢 我（需 gh 授权）+ 🔵 你（确认） |
| 2 | 创建 Gitee 仓库 | 🔵 你（网页） |
| 3 | 配置 git 身份 | 🟢 我（需你提供值） |
| 4 | 初始化并推送 | 🟢 我 |
| 5 | 配置 .gitignore | 🟢 我 |
| 6 | 上传 APK / Release | 混合 |
| 7 | 验证 | 混合 |

---

## 你当前需要决定的事项

1. ~~是否需要双平台~~ → **✅ 已确认：双平台**（GitHub 主仓 + Gitee 镜像/分发仓，2026-10-31）
2. ~~GitHub 授权方式~~ → **✅ 已确认：接受 `gh auth login` 浏览器一次性授权**（2026-10-31）
3. ~~仓库公开性：Public 还是 Private~~ → **✅ 已确认：Public**（2026-10-31）
4. ~~身份信息~~ → **✅ 已确认（2026-10-31）**：
   - **GitHub**：username `stonewd111`，noreply 邮箱 `350548336+stonewd111@users.noreply.github.com`
   - **Gitee**：username `stone`，邮箱 `17784442+waterstone01@user.noreply.gitee.com`
   - 公钥已粘贴到两个平台 SSH 设置（用户已完成）
