package org.dromara.system.domain.enums;

/**
 * OSS 图片、视频技术分类。
 *
 * <p>上传阶段使用该分类提前校验文件内容；业务绑定阶段仍由具体图片或视频字段确认最终分类。</p>
 */
public enum OssFileType {
    IMAGE,
    VIDEO
}
