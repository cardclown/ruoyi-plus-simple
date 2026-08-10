package org.dromara.content.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文章模块固定使用的字典类型。
 */
@Getter
@RequiredArgsConstructor
public enum ContentArticleDictType {

    /** 文章分类对应的固定字典类型。 */
    CATEGORY("content_article_category"),

    /** 文章标签对应的固定字典类型。 */
    TAG("content_article_tag");

    private final String dictType;
}
