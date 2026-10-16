package com.dsh.mobile.model;

/**
 * 抽屉里的「工作区」分组标题行。
 *
 * 改前分组标题是一个裸 String（只有显示名），点它除了展开/收起什么也做不了，
 * 于是「新建任务落到哪个工作区」在手机端根本无从选择 —— 这正是用户报的那个 bug。
 * 现在标题行带上定位这个工作区所需的两个标识，右侧「⋯」菜单才能把新会话派进指定工作区：
 *
 *   - {@link #path}        完整工作目录，直接作为新建会话的 cwd 发给网关
 *     （宿主契约：SessionCreateRequest.cwd，见 app.asar SessionCreateRequest 声明）；
 *   - {@link #workspaceId} 工作区注册表 id，来自网关 workspaces 帧；
 *     有它时优先用它（与电脑端 sessions.create({ workspaceId }) 完全一致）。
 *
 * 两个标识二选一发送：宿主明确拒绝同时给 workspaceId 与 cwd
 * （app.asar: session.create accepts workspaceId or cwd, not both）。
 */
public final class WorkspaceGroup {

    /**
     * 分组键（`MainActivity.buildRows()` 的分组键原样带出来）：
     * 规范化后的**小写完整路径**、`im:`（IM 会话）、空串（「其他」）。
     *
     * <p>折叠状态要用它当账本键：路径比"第几组"稳（组会随新会话/排序变化），
     * 也比显示名稳（「其他」和两个同名目录会撞车）。
     */
    public final String key;
    /** 标题上显示的工作区名（优先用工作区注册表里的 title，取不到时退回目录名）。 */
    public final String label;
    /** 完整工作目录；空表示这一组没有可派发的工作区（「其他」/ IM 会话）。 */
    public final String path;
    /** 工作区注册表 id；未取到（workspaces 帧还没到）时为空串。 */
    public final String workspaceId;
    /** 该组下的顶层会话数。 */
    public final int count;
    /**
     * 这一组名下的会话行**此刻是否铺在抽屉里**（派生字段，由 `buildRows` 每轮重算，不持久化）：
     * 折叠时只有本行可见、展开时才跟出会话行。默认值 true 只是构造期的安全兜底。
     */
    public boolean expanded = true;

    public WorkspaceGroup(String key, String label, String path, String workspaceId, int count) {
        this.key = key == null ? "" : key;
        this.label = label == null ? "" : label;
        this.path = path == null ? "" : path;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
        this.count = count;
    }

    /**
     * 这一组能不能往里新建任务。没有工作目录的分组（「其他」/ IM 会话）不行 ——
     * 给它们挂一个点了也没用的「⋯」只会误导用户。
     */
    public boolean canCreateSession() {
        return !path.isEmpty() || !workspaceId.isEmpty();
    }
}
