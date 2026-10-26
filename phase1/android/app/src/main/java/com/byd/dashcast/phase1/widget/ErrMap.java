package com.byd.dashcast.phase1.widget;

import java.util.HashMap;
import java.util.Map;

public final class ErrMap {

    private static final Map<String, String[]> M = new HashMap<String, String[]>();

    static {
        put("ok", "成功", "无");
        put("true", "成功", "无");

        put("invalid_request", "请求体无法解析（缺字段或类型错）", "改用协议沙盒逐字段试，检查 body 是不是合法 JSON");
        put("request_timeout", "等待固件应答超时", "加大读取超时；检查 USB 链路是否掉");
        put("reply_timeout_or_invalid", "应答超时或格式非法", "看原始应答；可能是分帧错了，改 Nf/NULL 分帧模式重试");
        put("connect_failed", "TCP 连不上", "确认 Network 选对了、主机 IP 与端口对");
        put("send_failed", "写入失败（连接已断）", "重连后再试");
        put("busy", "服务忙", "稍后重试，或先看 status 是否 RUNNING");
        put("status_busy", "status-service 正在被占用", "降低轮询频率");
        put("worker_failed", "工作线程崩溃", "断开重连让服务重建 worker");
        put("operation_busy", "上一个操作还没结束", "等待上一操作返回后再发下一个（串行化）");
        put("unknown", "固件返回未知状态", "用协议沙盒看原始应答");
        put("unavailable", "该能力当前不可用", "检查 status 应答里的 capabilities 是否含此能力");
        put("pending", "已排队未生效", "轮询 cast.status 等待 applied");
        put("queued", "已排队", "轮询 cast.status 等待落地");
        put("requested", "已下发请求", "轮询确认字段");
        put("request_id_conflict", "request_id 重复", "生成新的 request_id");
        put("request_superseded", "被更新的请求覆盖", "使用最新 revision 重发");
        put("request_incomplete", "请求字段不全", "补齐必填字段后重发");
        put("request_invalid_encoding", "请求编码非法", "body 必须是 UTF-8 JSON");
        put("request_length_overflow", "请求超长", "拆小 body（壁纸/mask 请分片）");
        put("request_allocation_failed", "固件内存分配失败", "重试；若频繁出现请重启仪表");
        put("duplicate", "重复请求", "无需处理");
        put("release", "资源已释放", "需要重新 cast.start");
        put("cleanup_unconfirmed", "上一次清理未完成确认", "等几秒重试；确认没有僵尸会话");
        put("not_started", "尚未启动", "先执行 cast.start");
        put("operation_busy2", "操作忙", "稍后重试");

        put("hmi_unavailable", "hmi 层不可达", "检查 rc_hmi.exe 是否活着：status.video.state");
        put("hmi_disconnected", "hmi 连接已断", "重连后再启，必要时 hmi.restart");
        put("hmi_cancelled", "操作被取消", "重发");
        put("hmi_send_failed", "写给 ui.so 失败", "重连；若持续请看系统日志");
        put("hmi_send_timeout", "写给 ui.so 超时", "加大超时重试");
        put("hmi_receive_failed", "读 ui.so 失败", "重连");
        put("hmi_reply_timeout", "ui.so 无应答", "重连；避免并发操作");
        put("hmi_reply_too_large", "ui.so 应答过大", "缩小请求范围");
        put("hmi_reply_invalid", "ui.so 应答非法", "记录原文反馈");
        put("hmi_reply_invalid_json", "ui.so 应答不是合法 JSON", "记录原文反馈");
        put("hmi_reply_invalid_encoding", "ui.so 应答编码非法", "记录原文反馈");
        put("hmi_reply_invalid_result", "ui.so 应答缺 result", "记录原文反馈");
        put("hmi_request_too_long", "请求超长", "拆小 body");
        put("hmi_sequence_exhausted", "序列号用完", "重新建立会话");
        put("hmi_not_in_normal_mode", "仪表不在普通模式", "先切回普通模式再操作");
        put("hmi_restart_unconfirmed", "重启未确认", "轮询 hmi.status 等待恢复");
        put("hmi_restart_failed_theme_restored", "重启失败但主题已还原", "手动重试一次");
        put("hmi_restart_failed_restore_failed", "重启失败且还原也失败", "断电重连仪表，重设主题");
        put("hmi_upgrade_required", "需要更高固件版本", "确认已刷 D2.03 及以上");

        put("cast_not_active", "当前没有活跃的投屏会话", "先执行 cast.start");
        put("cast_stage_expired", "投屏阶段过期", "重新 cast.start");
        put("cast_session_owns_hmi", "会话已被占用", "先 stop 再 start；不要并发");
        put("video_arm_failed", "视频通道预置失败", "检查 47782 是否连通（我们不需要 RPC 到 push 端）");
        put("video_busy_or_cleanup_unconfirmed", "视频忙或上次清理未确认", "等 3 秒后重 stop/start");
        put("video_cleanup_unconfirmed", "视频清理未确认", "等待 3 秒重试");
        put("video_keepalive_unavailable", "保活通道不可用", "重发 cast.keepalive 或重连");
        put("video_stop_failed", "停止视频失败", "重试 cast.stop，仍失败则隔离重连");
        put("video_upgrade_or_connection_required", "需要升级或先建立连接", "确认 video 已在推送: status.video.state");
        put("session_recovered_stop_required", "会话已恢复，需要显式 stop", "执行一次 cast.stop 清理");
        put("proxy_owner_unavailable", "代理持有者不在", "重启 instrument 模块或插拔 USB");
        put("revision_conflict", "revision 冲突", "用应答里的 revision 重发");
        put("revision_exhausted", "revision 溢出", "重新开关 widgets/cast");
        put("center_apply_failed", "中心区域应用失败", "用 hmi.center.get 取回支持的范围再设");
        put("dimensions_out_of_range", "坐标/尺寸越界", "检查 x/y/width/height 是否落在仪表分辨率内");
        put("invalid_merged_options", "合并后的配置项非法", "减少一次修改的字段数");
        put("invalid_or_unsupported_request", "请求不受支持", "该 op 在当前模式不可用");
        put("unsupported_request", "不支持的操作", "对照 Ops 全集检查拼写");
        put("persistent_hmi_or_guard_check_failed", "持久化检查失败（HMI 或守护不在）", "确认 overlay-service 在跑");

        put("file_missing_or_unsafe", "文件路径缺失或不安全", "检查 path 是否被允许");
        put("file_read_failed", "读文件失败", "确认文件存在且有权限");
        put("file_format_invalid", "文件格式非法", "按 schema 修正");
        put("file_version_changed", "文件版本变了", "重新拉取后再改");
        put("file_changed", "文件已变化", "重新拉取");
        put("requires_rgba8_noninterlaced", "图片必须是 RGBA8 且非隔行", "导出为 RGBA8888、非隔行 PNG");
        put("invalid_png", "PNG 非法", "重新导出 PNG");
        put("png_crc_failed", "PNG CRC 校验失败", "重新导出，别让工具破坏 CRC");
        put("png_filter_failed", "PNG 滤波不支持", "用不支持预测的方式导出");
        put("png_inflate_failed", "PNG 解压失败", "重新导出");
        put("invalid_jpeg", "JPEG 非法", "重新导出 JPEG");
        put("jpeg_timeout", "JPEG 处理超时", "缩小分辨率");
        put("unsupported_jpeg_colorspace", "JPEG 色彩空间不支持", "转 sRGB");
        put("invalid_kzb_v3", "Kanzi kzb v3 非法", "用官方工具重新导出");
        put("sha256_mismatch", "SHA256 校验不一致", "重新上传完整数据");
        put("inflate_init_failed", "zlib 初始化失败", "固件资源紧张，重试");
        put("upload_incomplete", "上传未完成", "重新上传并确保连接稳定");
        put("upload_peer_lost", "上传到一半对方断开", "重连后重传");
        put("upload_read_failed", "读源失败", "重选文件");
        put("upload_write_failed", "写目标失败", "检查仪表存储空间");
        put("upload_close_failed", "关闭上传失败", "重试一次");
        put("upload_sync_failed", "落盘同步失败", "重试一次");
        put("upload_directory_invalid", "目标目录非法", "只用固件声明的目录");
        put("upload_file_create_failed", "创建文件失败", "检查空间与权限");
        put("upload_storage_insufficient", "存储空间不足", "清理仪表存储后重试");

        put("stage_timeout", "阶段超时", "重来一次；串行化操作");
        put("apply_timeout", "下发应用超时", "轮询 status 看是否最终生效");
        put("feedback_timeout", "反馈超时", "轮询 hmi.status");
        put("center_confirm_timeout", "中心区域确认超时", "轮询 hmi.center.get");
        put("theme_feedback_timeout", "主题切换反馈超时", "轮询 theme.get");

        put("configuration_fields_only", "config.set 只允许配置类字段", "cover_width/cover_height 归 track.load，config.set 只发 cover_size");
        put("cover_size_mismatch", "封面尺寸与解码字节数不符", "track.load 必带 cover_width/cover_height，且 width×height×4 等于封面解码后字节数");
        put("track_mismatch", "track.sync 的曲目未登记或与已登记不一致", "等 track.load 成功登记该 track_id 后再发 track.sync");

        put("LOCAL_NO_NETWORK", "没有找到能连通仪表的网络", "执行自检；检查 USB 线是否剪了 +5V 且已改 State");
        put("LOCAL_IO", "网络读写异常", "看日志里的原始异常；多为链路瞬断");
        put("LOCAL_TIMEOUT", "读写超时", "加大超时或缩短请求");
        put("LOCAL_EMPTY_REPLY", "对方没有返回任何字节", "确认端口与分帧方式；尝试切换分帧模式");
        put("LOCAL_BAD_JSON", "返回不是合法 JSON", "在沙盒里看原始字节");
        put("LOCAL_INTERRUPTED", "操作被中断", "重试");
    }

    private static void put(String k, String reason, String action) {
        M.put(k, new String[]{reason, action});
    }

    public static String[] lookup(String code) {
        if (code == null) return new String[]{"未知错误", "无"};
        String[] v = M.get(code.trim());
        if (v != null) return v;
        String lower = code.trim().toLowerCase();
        String[] v2 = M.get(lower);
        if (v2 != null) return v2;
        return new String[]{"固件返回未登记的状态: " + code, "把原文贴出来补进 ErrMap"};
    }

    public static String reason(String code) {
        return lookup(code)[0];
    }

    public static String action(String code) {
        return lookup(code)[1];
    }
}
