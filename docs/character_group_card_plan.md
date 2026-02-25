# 群组角色卡（Group Card）规划

## 1. 现状定位（当前角色卡配置在哪里）

### 1.1 角色卡数据与存储
- `app/src/main/java/com/ai/assistance/operit/data/model/CharacterCard.kt`
  - 当前角色卡结构：`id/name/description/characterSetting/openingStatement/otherContent/attachedTagIds/advancedCustomPrompt/marks/...`
- `app/src/main/java/com/ai/assistance/operit/data/preferences/CharacterCardManager.kt`
  - 角色卡存储在 DataStore `character_cards`
  - Key 形如 `character_card_${id}_xxx`
  - 活跃角色卡：`active_character_card_id`
  - 提示词拼接入口：`combinePrompts(characterCardId, additionalTagIds)`

### 1.2 提示词管理界面
- `app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ModelPromptsSettingsScreen.kt`
  - 当前 Tab 只有两页：角色卡 + 标签
  - 角色卡新增/编辑/删除/复制/导入导出都在这里

### 1.3 发送链路（你要的“自动多角色发言”必须改这里）
- `MessageCoordinationDelegate.sendUserMessage(...)`
  -> `MessageProcessingDelegate.sendUserMessage(...)`
  -> `AIMessageManager.sendMessage(...)`
  -> `EnhancedAIService.sendMessage(...)`
  -> `ConversationService.prepareConversationHistory(...)`

### 1.4 可复用能力（对“群组”很有用）
- 已支持“代发标签”`<proxy_sender name="..."/>`：
  - 构建：`AIMessageManager.buildUserMessageContent(...)`
  - 解析显示：`BubbleUserMessageComposable.kt`
- AI消息已持久化角色名 `roleName`：
  - `ChatMessage.kt` / `MessageEntity.kt`
- 已有“角色卡主题绑定”和“Waifu配置绑定”：
  - `UserPreferencesManager` 的 `character_card_theme_*`
  - `WaifuPreferences` 的 `character_card_waifu_*`

## 2. 目标能力拆解

新增一种“群组角色卡（Group Card）”，要求：
1. 在提示词管理页新增独立 Tab：`群组`
2. 群组可包含多个角色卡成员
3. 群组配置支持：
   - 增删成员
   - 拖动发言顺序
   - 发言概率
   - 随机性调节
   - 发言间隔
4. 选中群组后，用户发一句，系统自动按群组策略轮流发言
5. 关键：后续角色发言时，必须把前面“用户 + 其他AI”的发言拼到新一轮用户输入里，并标注发言人
6. 群组享受和角色卡相同的主题绑定能力
7. 角色卡编辑页支持“对话模型配置”：
   - 跟随全局 CHAT 功能模型（默认）
   - 绑定指定模型配置（可含模型索引）
8. 上述角色卡对话模型绑定是独立覆盖层，不影响功能模型映射（`FunctionType -> config`）

## 3. 数据结构设计（新增）

建议新增模型：
- `CharacterGroupCard`
  - `id/name/description`
  - `members: List<GroupMemberConfig>`
  - `enabled`
  - `roundsPerUserMessage`（一次用户输入触发几轮）
  - `globalMinIntervalMs/globalMaxIntervalMs`
  - `randomness`（0~1）
  - `createdAt/updatedAt`
- `GroupMemberConfig`
  - `characterCardId`
  - `orderIndex`
  - `speakProbability`（0~1）
  - `randomnessBias`（可选）
  - `minIntervalMs/maxIntervalMs`（成员级覆盖）
  - `enabled`

存储建议：
- 新建 `CharacterGroupCardManager`，DataStore：`character_groups`
- key 风格与角色卡一致，避免和聊天分组 `group` 字段冲突（命名统一叫 `character_group_*`）

### 3.1 角色卡对话模型绑定（前置于群组改造）

建议新增结构（可先只做角色卡，群组后续复用）：
- `ChatModelBindingMode`
  - `FOLLOW_GLOBAL`（默认）
  - `FIXED_CONFIG`
- `ChatModelBinding`
  - `mode: ChatModelBindingMode`
  - `configId: String?`
  - `modelIndex: Int`（默认 `0`）

角色卡侧建议新增字段：
- `CharacterCard.chatModelBindingMode: String = FOLLOW_GLOBAL`
- `CharacterCard.chatModelConfigId: String? = null`
- `CharacterCard.chatModelIndex: Int = 0`

关键约束：
- 这是 CHAT 场景覆盖层，不修改 `FunctionalConfigManager` 对其他 `FunctionType`（SUMMARY/TRANSLATION/...）的映射。
- 当 `mode=FOLLOW_GLOBAL` 时，仍走现有 `FunctionType.CHAT` 映射。
- 当 `mode=FIXED_CONFIG` 时，仅覆盖当前轮 CHAT 请求使用的模型配置。

## 4. UI 改造规划

## 4.1 提示词管理页新增第三个 Tab
- 文件：`ModelPromptsSettingsScreen.kt`
- 现有 `currentTab` 从 2 页扩展为 3 页：
  - `0 角色卡`
  - `1 标签`
  - `2 群组`

## 4.2 新增群组管理界面
- `GroupCardTab`（列表）
  - 新建/编辑/删除/复制群组
  - 显示成员数、启用状态
- `GroupCardDialog`（编辑器）
  - 成员选择（从现有角色卡池）
  - 拖动排序（若先做 MVP，可先上下移动按钮，后续再上拖拽）
  - 概率、随机性、间隔参数输入

## 4.3 聊天界面选择器扩展
- `CharacterSelectorPanel.kt` / `ChatScreenHeader.kt`
- 支持选择“角色卡或群组”
- 选中群组后，Header 显示群组名（可加标记“群组”）
- 同步改 `ChatScreenContent` 的 `CURRENT_CHARACTER_ONLY` 过滤逻辑，不能再只依赖 `characterCardName`
- 聊天切换时“自动切角色卡”是可选开关，群组模式要定义优先级：会话绑定优先还是当前激活项优先

## 5. 对话调度方案（核心）

新增 `GroupConversationOrchestrator`（建议放 `services/core`）。

触发时机：
- 在 `MessageCoordinationDelegate.sendMessageInternal(...)` 里检测当前是否激活群组
- 若激活群组，进入“群组模式流程”；否则走原流程

群组模式流程（建议）：
1. 用户原始消息先入库（保留可见）
2. 生成本轮“候选发言成员序列”
   - 基于顺序 + 概率 + 随机性筛选
3. 逐个成员执行发送（串行，等待上一个成员完成）
4. 每个成员发送前构造“拼接上下文用户输入”，格式示例：
   - `用户: ...`
   - `AI(角色A): ...`
   - `AI(角色B): ...`
   - `请你以 角色C 身份继续发言`
5. 调用现有发送接口时指定 `roleCardIdOverride = 当前成员角色卡ID`
6. 成员间按配置延时（发言间隔）

### 5.1 编排完成判定（必做）
- 现有 `sendUserMessage(...)` 为异步入口，方法返回不等于“该成员发言完成”。
- 该轮完成至少要覆盖：流结束 + 收尾完成（含 `onTurnComplete` 语义）。
- `Waifu` 模式下存在主流结束后的异步拆句发送，不能只看 `activeStreamingChatIds`/`isLoading`。
- 建议为编排器定义统一 `awaitMemberTurnComplete(chatId, timeout)`，由编排器内部决定完成条件。

### 5.2 发送语义与副作用（必做）
- `chatIdOverride` 在当前实现中会触发“后台发送”语义（附件/reply_to/总结/UI流程行为不同）。
- 群聊编排若只是“固定当前会话”，不要默认走后台发送语义，需显式选择行为。
- 工具链（`StandardChatManagerTool`）也依赖同一发送链，群聊改造不能破坏其既有语义。

### 5.3 上下文构造与历史写入（必做）
- 当前发送链会自动写入 `sender=user` 消息；群聊成员轮次不能把“内部拼接文本”直接落库为用户可见消息。
- `AIMessageManager.getMemoryFromMessages(...)` 不带 `roleName`，发言人标注必须在编排器里显式构造。
- `ConversationService.processChatMessageWithTools(...)` 会重分配 XML 片段为 `assistant/user`，群聊拼接时要考虑该语义。

### 5.4 角色卡模型覆盖解析链（前置）
- 在 `MessageCoordinationDelegate.sendMessageInternal(...)` 中，先按 `roleCardId` 解析角色卡模型绑定策略。
- 若 `FOLLOW_GLOBAL`：沿用当前 `FunctionType.CHAT` 配置。
- 若 `FIXED_CONFIG`：为本次发送注入 `chatModelConfigIdOverride/chatModelIndexOverride`。
- 覆盖仅作用于当前 CHAT 请求，不改全局配置，不写回功能模型映射。

### 5.5 群组总结策略（必做）
- 群组成员轮次执行期间，统一关闭“每次发送前的自动总结检查”，避免中途插入总结打断编排顺序。
- 群组总结触发时机改为“整轮成员发言完成后”再评估一次（按整轮维度而非单成员维度）。
- 群组模式下禁用“总结后自动续发（autoContinue）”，避免与编排器的下一成员调度冲突。
- 群组总结文本必须保留发言人信息（`用户/AI(角色名)`），禁止退化为仅 `assistant/user` 二值语义。

## 6. 主题绑定能力复用方案

为了让群组也享受主题绑定，建议把现有“角色卡主题绑定”抽象成“实体主题绑定”：
- `UserPreferencesManager`
  - 从 `character_card_theme_${id}_*` 扩展为可支持 `character_group_theme_${id}_*`
- `WaifuPreferences`
  - 从 `character_card_waifu_${id}_*` 扩展为可支持 `character_group_waifu_${id}_*`

然后在 `CharacterGroupCardManager.setActiveGroup(...)` 时：
- `switchToGroupTheme(groupId)`
- `switchToGroupWaifuSettings(groupId)`

同时要补齐生命周期能力（不只是 switch）：
- `createDefault*`（新建群组时）
- `clone*`（复制群组时）
- `delete*`（删除群组时）
- `has*`（判断是否有绑定）

## 7. 聊天绑定与持久化（建议）

当前聊天只绑定 `characterCardName`（`ChatEntity.characterCardName`），属于字符串绑定。

建议新增：
- `ChatEntity.characterGroupId: String?`
- `ChatHistory.characterGroupId: String?`
- Room 迁移：数据库版本 +1，`ALTER TABLE chats ADD COLUMN characterGroupId TEXT`

### 7.1 绑定策略与迁移策略
- 群组绑定统一使用稳定 ID，不使用群组名称做主绑定键。
- 保留现有 `characterCardName` 的兼容读取；新逻辑优先读取 `characterGroupId`。
- 明确角色卡改名/删除后的同步策略，避免只停留在“提示去聊天管理”。

### 7.2 开场白同步冲突处理
- 当前 `syncOpeningStatementIfNoUserMessage(...)` 会在“无用户消息”时改写/删除首条 AI 消息。
- 群组会话需要单独定义开场白策略（禁用、独立字段或专门同步逻辑），避免误改。

### 7.3 备份、导入导出与兼容
- 角色卡备份 schema 需要扩展群组数据（当前 `operit_character_cards_backup_v1` 只含 cards/tags）。
- 聊天导入导出基于 `ChatHistory` 序列化，新增字段需保证旧文件可导入（`ignoreUnknownKeys` 兼容）。
- Room 迁移脚本与导入导出字段要同步提交，避免“DB有字段、导入丢字段”。

### 7.4 发送函数参数扩展（为模型覆盖准备）
- `MessageCoordinationDelegate.sendUserMessage(...)` / `sendMessageInternal(...)` 增加可选参数：
  - `chatModelConfigIdOverride: String? = null`
  - `chatModelIndexOverride: Int? = null`
- `MessageProcessingDelegate.sendUserMessage(...)`、`AIMessageManager.sendMessage(...)`、`EnhancedAIService.sendMessage(...)` 透传上述参数。
- `EnhancedAIService` 在 `functionType=CHAT` 时优先使用 override；否则走原 `getServiceForFunction(FunctionType.CHAT)`。
- provider/model 显示信息也应基于实际生效配置，避免消息头显示与真实调用模型不一致。

## 8. 分阶段落地

### Phase 0（前置）
1. 实现角色卡对话模型绑定（`FOLLOW_GLOBAL/FIXED_CONFIG`）
2. 角色卡编辑页增加“绑定模型配置/跟随全局”选项
3. 发送链增加 `chatModelConfigIdOverride/chatModelIndexOverride` 并打通到 `EnhancedAIService`
4. 保证功能模型映射体系不受影响（仅 CHAT 请求可被角色卡覆盖）

### Phase 1（MVP）
1. 新增群组模型 + Manager + DataStore
2. 提示词管理页新增“群组”Tab（先做可编辑，不做拖拽）
3. 聊天支持选择群组（选择器/头部/历史过滤联动）
4. 编排器实现串行自动发言 + 发言间隔
5. 实现编排器级“成员发言完成判定”
6. 实现“拼接前序 AI/用户内容并标注发言人”，且内部拼接文本不污染可见聊天历史
7. 实现群组专用总结策略（轮次后总结、禁用中途总结与 autoContinue）

### Phase 2（增强）
1. 成员拖拽排序
2. 概率和随机性策略精细化
3. 聊天级群组绑定（DB 字段 + 自动恢复）
4. 群组主题/Waifu绑定完整接入（create/clone/switch/delete/has）
5. 备份、导入导出、迁移链路打通

## 9. 风险与注意点

1. 命名冲突风险：
   - 项目已有“聊天分组 group”，新能力必须统一命名为 `character_group`，避免歧义
2. 历史消息膨胀风险：
   - 多轮自动发言会快速增长上下文，需配合总结阈值与轮次数限制
3. 并发与时序风险：
   - 群组发言必须串行等待前一个角色完成，且完成判定不能只依赖单一状态位
   - 若总结在成员轮次中途触发，会与编排调度冲突，必须按“整轮后总结”处理
4. 名称耦合风险：
   - 现有多处按角色名称反查（头像/roleName/proxy_sender），重名与改名会影响历史展示一致性
5. 模型一致性风险：
   - 若“显示的 provider/model”与“实际生效 override 配置”不一致，会误导用户排障
6. 兼容风险：
   - 已发布版本需走向前兼容（新增字段 + 旧流程可用 + 导入导出可读）
7. 工具链回归风险：
   - `StandardChatManagerTool` 走同一发送链，群聊改造后必须回归验证该路径语义

## 10. 实施前需你确认（按你的仓库规则）

这次属于“方案扩展/升级”。请先确认：
1. 当前线上是否已发布这个版本？
2. 如果已发布，我按“向前兼容迁移”执行；
3. 如果未发布，可按“彻底重构清理旧方案”执行。
